package com.smartline.smartos.modules.dashboard;

import com.smartline.smartos.modules.audit.AuditLog;
import com.smartline.smartos.modules.audit.AuditLogRepository;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.Book.BookStatus;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.book.ContributorRepository;
import com.smartline.smartos.modules.dashboard.DashboardDtos.ActivityItem;
import com.smartline.smartos.modules.dashboard.DashboardDtos.AttentionItem;
import com.smartline.smartos.modules.dashboard.DashboardDtos.DashboardSummary;
import com.smartline.smartos.modules.dashboard.DashboardDtos.PipelineSlice;
import com.smartline.smartos.modules.dashboard.DashboardDtos.Trends;
import com.smartline.smartos.modules.finance.RoyaltyStatement;
import com.smartline.smartos.modules.finance.RoyaltyStatementRepository;
import com.smartline.smartos.modules.inventory.InventoryService;
import com.smartline.smartos.modules.inventory.InventoryDtos.StockLevelResponse;
import com.smartline.smartos.modules.manuscript.Manuscript;
import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.task.Task;
import com.smartline.smartos.modules.task.Task.TaskStatus;
import com.smartline.smartos.modules.task.TaskRepository;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

  private static final int LOW_STOCK_THRESHOLD = 10;
  private static final int MAX_ATTENTION = 8;

  private final BookRepository books;
  private final ContributorRepository contributors;
  private final ManuscriptRepository manuscripts;
  private final TaskRepository tasks;
  private final RoyaltyStatementRepository statements;
  private final AuditLogRepository auditLogs;
  private final AppUserRepository users;
  private final InventoryService inventory;

  public DashboardService(BookRepository books, ContributorRepository contributors,
      ManuscriptRepository manuscripts, TaskRepository tasks,
      RoyaltyStatementRepository statements, AuditLogRepository auditLogs,
      AppUserRepository users, InventoryService inventory) {
    this.books = books;
    this.contributors = contributors;
    this.manuscripts = manuscripts;
    this.tasks = tasks;
    this.statements = statements;
    this.auditLogs = auditLogs;
    this.users = users;
    this.inventory = inventory;
  }

  public DashboardSummary summary() {
    UUID orgId = TenantContext.require();
    List<Book> allBooks = books.findByOrganization_Id(orgId, Pageable.unpaged()).getContent();
    long published = allBooks.stream().filter(b -> b.getStatus() == BookStatus.PUBLISHED).count();
    int totalContributors = contributors.findByOrganization_Id(orgId).size();
    List<RoyaltyStatement> allStatements =
        statements.findByOrganization_Id(orgId, Pageable.unpaged()).getContent();
    BigDecimal pending = BigDecimal.ZERO;
    for (RoyaltyStatement s : allStatements) {
      if (s.getStatus() == RoyaltyStatement.StatementStatus.DRAFT
          || s.getStatus() == RoyaltyStatement.StatementStatus.APPROVED) {
        pending = pending.add(s.getPayable());
      }
    }
    List<Manuscript> allManuscripts =
        manuscripts.findByOrganization_Id(orgId, Pageable.unpaged()).getContent();

    DashboardSummary summary = new DashboardSummary();
    summary.setTotalBooks(allBooks.size());
    summary.setPublishedBooks((int) published);
    summary.setTotalContributors(totalContributors);
    summary.setPendingRoyalties(pending);
    summary.setManuscriptPipeline(manuscriptPipeline(allManuscripts));
    summary.setTitlePipeline(titlePipeline(allBooks));
    summary.setAttention(attention(orgId, allManuscripts));
    summary.setActivity(activity(orgId));
    summary.setTrends(trends(allBooks, allStatements));
    return summary;
  }

  private List<PipelineSlice> manuscriptPipeline(List<Manuscript> all) {
    Map<ManuscriptStage, Integer> counts = new HashMap<>();
    for (Manuscript m : all) {
      counts.merge(m.getStage(), 1, Integer::sum);
    }
    List<PipelineSlice> slices = new ArrayList<>();
    slices.add(new PipelineSlice("Submitted", counts.getOrDefault(ManuscriptStage.SUBMITTED, 0)));
    slices.add(new PipelineSlice("In review", counts.getOrDefault(ManuscriptStage.REVIEW, 0)));
    slices.add(new PipelineSlice("Editing", counts.getOrDefault(ManuscriptStage.EDITING, 0)));
    slices.add(new PipelineSlice("Proof", counts.getOrDefault(ManuscriptStage.PROOF, 0)));
    slices.add(new PipelineSlice("Approved", counts.getOrDefault(ManuscriptStage.APPROVED, 0)));
    return slices;
  }

  private List<PipelineSlice> titlePipeline(List<Book> all) {
    Map<BookStatus, Integer> counts = new HashMap<>();
    for (Book b : all) {
      counts.merge(b.getStatus(), 1, Integer::sum);
    }
    List<PipelineSlice> slices = new ArrayList<>();
    slices.add(new PipelineSlice("Draft", counts.getOrDefault(BookStatus.DRAFT, 0)));
    slices.add(new PipelineSlice("In edit", counts.getOrDefault(BookStatus.IN_EDIT, 0)));
    slices.add(new PipelineSlice("Designed", counts.getOrDefault(BookStatus.DESIGNED, 0)));
    slices.add(new PipelineSlice("Printing", counts.getOrDefault(BookStatus.PRINTING, 0)));
    slices.add(new PipelineSlice("Published", counts.getOrDefault(BookStatus.PUBLISHED, 0)));
    slices.add(new PipelineSlice("Archived", counts.getOrDefault(BookStatus.ARCHIVED, 0)));
    return slices;
  }

  private List<AttentionItem> attention(UUID orgId, List<Manuscript> allManuscripts) {
    List<AttentionItem> items = new ArrayList<>();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    tasks.findByOrganization_Id(orgId, Pageable.unpaged()).getContent().stream()
        .filter(t -> t.getStatus() != TaskStatus.DONE
            && t.getDueDate() != null && t.getDueDate().isBefore(today))
        .sorted(Comparator.comparing(Task::getDueDate))
        .limit(3)
        .forEach(t -> items.add(new AttentionItem("task-" + t.getId(), "task",
            "Overdue: " + t.getTitle(), "Was due " + t.getDueDate(), "high",
            "Overdue " + t.getDueDate(), "/tasks")));
    inventory.levels(null, null, null).stream()
        .filter(l -> l.getQuantity() < LOW_STOCK_THRESHOLD)
        .sorted(Comparator.comparingInt(StockLevelResponse::getQuantity))
        .limit(3)
        .forEach(l -> items.add(new AttentionItem("stock-" + l.getBookId() + "-" + l.getWarehouseId(),
            "stock", "Low stock — " + l.getBookTitle(),
            l.getQuantity() + " copies left at " + l.getWarehouseName(), "medium", null,
            "/inventory")));
    allManuscripts.stream()
        .filter(m -> m.getStage() == ManuscriptStage.SUBMITTED
            || m.getStage() == ManuscriptStage.REVIEW)
        .sorted(Comparator.comparing(Manuscript::getCreatedAt,
            Comparator.nullsLast(Comparator.naturalOrder())))
        .limit(2)
        .forEach(m -> items.add(new AttentionItem("manuscript-" + m.getId(), "approval",
            "Review needed — " + m.getTitle(), "Awaiting editorial decision", "medium", null,
            "/manuscripts")));
    long drafts = statements.findByOrganization_IdAndStatus(orgId,
            RoyaltyStatement.StatementStatus.DRAFT, Pageable.unpaged()).getTotalElements();
    if (drafts > 0) {
      items.add(new AttentionItem("royalties-draft", "royalty",
          drafts + (drafts == 1 ? " royalty statement" : " royalty statements")
              + " awaiting approval",
          "Review and approve pending payouts", "medium", null, "/royalties"));
    }
    return items.stream().limit(MAX_ATTENTION).toList();
  }

  private List<ActivityItem> activity(UUID orgId) {
    List<AuditLog> logs = auditLogs.findTop8ByOrganizationIdOrderByCreatedAtDesc(orgId);
    Map<UUID, String> names = new HashMap<>();
    users.findAllById(logs.stream().map(AuditLog::getActorId)
            .filter(id -> id != null)
            .distinct()
            .toList())
        .forEach(u -> names.put(u.getId(), u.getFullName()));
    List<ActivityItem> feed = new ArrayList<>();
    for (AuditLog log : logs) {
      String actor = log.getActorId() != null
          ? names.getOrDefault(log.getActorId(), "Staff")
          : "System";
      feed.add(new ActivityItem(log.getId().toString(), actor, humanize(log.getAction()),
          log.getCreatedAt() != null ? log.getCreatedAt().toString() : null));
    }
    return feed;
  }

  private String humanize(String action) {
    if (action == null || action.isBlank()) {
      return "performed an action";
    }
    return switch (action) {
      case "book.create", "customer.create", "order.create", "task.create", "warehouse.create",
           "production.job.create", "production.run.create", "finance.budget.create",
           "royalty.contract.create", "user.create", "organization.create", "manuscript.create",
           "contributor.create" ->
        "created " + subject(action);
      case "book.update", "customer.update", "task.update", "manuscript.update",
           "finance.budget.update", "user.update" ->
        "updated " + subject(action);
      case "book.delete", "customer.delete", "task.delete", "warehouse.delete",
           "production.job.delete", "production.run.delete", "finance.budget.delete",
           "royalty.contract.delete", "user.delete", "manuscript.delete" ->
        "deleted " + subject(action);
      case "manuscript.transition" -> "moved a manuscript to a new stage";
      case "order.confirm" -> "confirmed an order";
      case "order.fulfil" -> "fulfilled an order";
      case "order.cancel" -> "cancelled an order";
      case "production.job.start" -> "started a print job";
      case "production.job.complete" -> "completed a print job";
      case "production.job.cancel" -> "cancelled a print job";
      case "production.run.post" -> "posted a print run into stock";
      case "stock.movement" -> "recorded a stock movement";
      case "stock.transfer" -> "transferred stock";
      case "finance.transaction.record" -> "recorded a transaction";
      case "royalty.contract.terminate" -> "terminated a contract";
      case "royalty.statement.generate" -> "generated a royalty statement";
      case "royalty.statement.approve" -> "approved a royalty statement";
      case "royalty.statement.pay" -> "paid a royalty statement";
      default -> action.replace('.', ' ').replace('_', ' ');
    };
  }

  private String subject(String action) {
    String[] parts = action.split("\\.", 2);
    if (parts.length < 2) {
      return "a record";
    }
    return switch (parts[0] + "." + parts[1]) {
      case "production.run" -> "a print run";
      case "production.job" -> "a print job";
      case "finance.budget" -> "a budget";
      case "finance.transaction" -> "a transaction";
      case "royalty.contract" -> "a contract";
      case "royalty.statement" -> "a statement";
      case "order.create", "order.update", "order.delete" -> "an order";
      case "organization.create" -> "an organization";
      default -> "a " + parts[0];
    };
  }

  private Trends trends(List<Book> allBooks, List<RoyaltyStatement> allStatements) {
    List<YearMonth> months = new ArrayList<>();
    YearMonth now = YearMonth.now(ZoneOffset.UTC);
    for (int i = 11; i >= 0; i--) {
      months.add(now.minusMonths(i));
    }
    List<Integer> bookCounts = new ArrayList<>();
    List<BigDecimal> royaltySums = new ArrayList<>();
    for (YearMonth month : months) {
      int titles = 0;
      for (Book b : allBooks) {
        if (b.getCreatedAt() != null
            && !YearMonth.from(b.getCreatedAt().atZone(ZoneOffset.UTC)).isAfter(month)) {
          titles++;
        }
      }
      bookCounts.add(titles);
      BigDecimal royalties = BigDecimal.ZERO;
      for (RoyaltyStatement s : allStatements) {
        if (s.getCreatedAt() != null
            && YearMonth.from(s.getCreatedAt().atZone(ZoneOffset.UTC)).equals(month)) {
          royalties = royalties.add(s.getPayable());
        }
      }
      royaltySums.add(royalties);
    }
    return new Trends(bookCounts, royaltySums);
  }
}
