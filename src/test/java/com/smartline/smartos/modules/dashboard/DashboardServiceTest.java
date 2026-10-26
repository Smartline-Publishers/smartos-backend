package com.smartline.smartos.modules.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartline.smartos.modules.audit.AuditLog;
import com.smartline.smartos.modules.audit.AuditLogRepository;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.Book.BookStatus;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.book.ContributorRepository;
import com.smartline.smartos.modules.dashboard.DashboardDtos.DashboardSummary;
import com.smartline.smartos.modules.finance.RoyaltyStatement;
import com.smartline.smartos.modules.finance.RoyaltyStatement.StatementStatus;
import com.smartline.smartos.modules.finance.RoyaltyStatementRepository;
import com.smartline.smartos.modules.inventory.InventoryDtos.StockLevelResponse;
import com.smartline.smartos.modules.inventory.InventoryService;
import com.smartline.smartos.modules.manuscript.Manuscript;
import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.task.Task;
import com.smartline.smartos.modules.task.Task.TaskStatus;
import com.smartline.smartos.modules.task.TaskRepository;
import com.smartline.smartos.modules.user.AppUser;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private BookRepository books;
  @Mock private ContributorRepository contributors;
  @Mock private ManuscriptRepository manuscripts;
  @Mock private TaskRepository tasks;
  @Mock private RoyaltyStatementRepository statements;
  @Mock private AuditLogRepository auditLogs;
  @Mock private AppUserRepository users;
  @Mock private InventoryService inventory;

  private DashboardService service;
  private UUID orgId;
  private UUID userId;
  private Organization org;
  private UserPrincipal principal;

  @BeforeEach
  void setUp() {
    service = new DashboardService(books, contributors, manuscripts, tasks, statements,
        auditLogs, users, inventory);
    orgId = UUID.randomUUID();
    userId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    principal = new UserPrincipal(userId, orgId, List.of(Role.ORG_ADMIN.name()));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void summaryComposesTotalsAttentionActivityAndTrends() {
    Instant now = Instant.now();
    Book published = book("Maths 4", BookStatus.PUBLISHED, now);
    Book draft = book("Akan Tales", BookStatus.DRAFT, now);
    when(books.findByOrganization_Id(eq(orgId), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(published, draft)));
    when(contributors.findByOrganization_Id(eq(orgId))).thenReturn(List.of());
    RoyaltyStatement pending = new RoyaltyStatement();
    pending.setId(UUID.randomUUID());
    pending.setOrganization(org);
    pending.setStatus(StatementStatus.DRAFT);
    pending.setPayable(new BigDecimal("100.00"));
    pending.setCreatedAt(now);
    when(statements.findByOrganization_Id(eq(orgId), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(pending)));
    when(statements.findByOrganization_IdAndStatus(eq(orgId), eq(StatementStatus.DRAFT),
        eq(Pageable.unpaged()))).thenReturn(new PageImpl<>(List.of(pending)));
    Manuscript review = new Manuscript();
    review.setId(UUID.randomUUID());
    review.setOrganization(org);
    review.setTitle("Ga Proverbs");
    review.setStage(ManuscriptStage.REVIEW);
    review.setCreatedAt(now);
    when(manuscripts.findByOrganization_Id(eq(orgId), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(review)));
    Task overdue = new Task();
    overdue.setId(UUID.randomUUID());
    overdue.setOrganization(org);
    overdue.setTitle("Fix proof");
    overdue.setStatus(TaskStatus.TODO);
    overdue.setDueDate(LocalDate.now().minusDays(2));
    when(tasks.findByOrganization_Id(eq(orgId), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(overdue)));
    when(inventory.levels(eq(null), eq(null), eq(null))).thenReturn(List.of(
        new StockLevelResponse(published.getId(), "Maths 4", UUID.randomUUID(), "Accra", 3)));
    AppUser actor = new AppUser();
    actor.setId(UUID.randomUUID());
    actor.setFullName("Ama Serwaa");
    AuditLog log = new AuditLog();
    log.setId(UUID.randomUUID());
    log.setActorId(actor.getId());
    log.setAction("book.create");
    log.setCreatedAt(now);
    when(auditLogs.findTop8ByOrganizationIdOrderByCreatedAtDesc(eq(orgId)))
        .thenReturn(List.of(log));
    when(users.findAllById(any())).thenReturn(List.of(actor));

    DashboardSummary summary = service.summary(principal);

    assertEquals(2, summary.getTotalBooks());
    assertEquals(1, summary.getPublishedBooks());
    assertEquals(0, new BigDecimal("100.00").compareTo(summary.getPendingRoyalties()));
    assertTrue(summary.getAttention().stream().anyMatch(a -> a.getId().startsWith("task-")));
    assertTrue(summary.getAttention().stream().anyMatch(a -> a.getId().startsWith("stock-")));
    assertTrue(summary.getAttention().stream().anyMatch(a -> a.getId().startsWith("manuscript-")));
    assertEquals("Ama Serwaa", summary.getActivity().get(0).getActor());
    assertEquals("created a book", summary.getActivity().get(0).getAction());
    assertEquals(12, summary.getTrends().getBooks().size());
    assertEquals(2, summary.getTrends().getBooks().get(11).intValue());
    assertEquals(5, summary.getManuscriptPipeline().size());
    assertEquals(6, summary.getTitlePipeline().size());
  }

  private Book book(String title, BookStatus status, Instant createdAt) {
    Book book = new Book();
    book.setId(UUID.randomUUID());
    book.setOrganization(org);
    book.setTitle(title);
    book.setStatus(status);
    book.setCreatedAt(createdAt);
    return book;
  }
}
