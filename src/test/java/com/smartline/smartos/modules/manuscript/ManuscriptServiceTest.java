package com.smartline.smartos.modules.manuscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.book.ContributorRepository;
import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.CreateManuscriptRequest;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.UpdateManuscriptRequest;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ManuscriptServiceTest {

  @Mock private ManuscriptRepository manuscripts;
  @Mock private ManuscriptEventRepository events;
  @Mock private OrganizationRepository organizations;
  @Mock private BookRepository books;
  @Mock private ContributorRepository contributors;
  @Mock private AppUserRepository users;

  private ManuscriptService service;
  private UUID orgId;
  private Organization org;

  @BeforeEach
  void setUp() {
    service = new ManuscriptService(manuscripts, events, organizations, books, contributors, users);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createDefaultsToSubmittedAndRecordsSubmissionEvent() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(manuscripts.save(any(Manuscript.class))).thenAnswer(inv -> inv.getArgument(0));
    when(events.save(any(ManuscriptEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateManuscriptRequest req = new CreateManuscriptRequest();
    req.setTitle("Ga Proverbs");
    Manuscript saved = service.create(req);

    assertEquals(ManuscriptStage.SUBMITTED, saved.getStage());
    assertEquals(1, saved.getVersion());
    ArgumentCaptor<ManuscriptEvent> event = ArgumentCaptor.forClass(ManuscriptEvent.class);
    verify(events).save(event.capture());
    assertEquals(ManuscriptStage.SUBMITTED, event.getValue().getToStage());
  }

  @Test
  void illegalTransitionThrows400AndRecordsNothing() {
    Manuscript manuscript = manuscriptAt(ManuscriptStage.SUBMITTED);
    when(manuscripts.findByIdAndOrganizationId(eq(manuscript.getId()), eq(orgId)))
        .thenReturn(Optional.of(manuscript));

    ApiException ex = assertThrows(ApiException.class,
        () -> service.transition(manuscript.getId(), ManuscriptStage.APPROVED, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verify(events, never()).save(any());
  }

  @Test
  void sameStageTransitionThrows400() {
    Manuscript manuscript = manuscriptAt(ManuscriptStage.REVIEW);
    when(manuscripts.findByIdAndOrganizationId(eq(manuscript.getId()), eq(orgId)))
        .thenReturn(Optional.of(manuscript));

    assertThrows(ApiException.class,
        () -> service.transition(manuscript.getId(), ManuscriptStage.REVIEW, null));
  }

  @Test
  void legalTransitionMovesStageAndRecordsEvent() {
    Manuscript manuscript = manuscriptAt(ManuscriptStage.SUBMITTED);
    when(manuscripts.findByIdAndOrganizationId(eq(manuscript.getId()), eq(orgId)))
        .thenReturn(Optional.of(manuscript));
    when(manuscripts.save(any(Manuscript.class))).thenAnswer(inv -> inv.getArgument(0));
    when(events.save(any(ManuscriptEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    Manuscript moved = service.transition(manuscript.getId(), ManuscriptStage.REVIEW, "Looks good");

    assertEquals(ManuscriptStage.REVIEW, moved.getStage());
    ArgumentCaptor<ManuscriptEvent> event = ArgumentCaptor.forClass(ManuscriptEvent.class);
    verify(events).save(event.capture());
    assertEquals(ManuscriptStage.SUBMITTED, event.getValue().getFromStage());
    assertEquals(ManuscriptStage.REVIEW, event.getValue().getToStage());
    assertEquals("Looks good", event.getValue().getComment());
  }

  @Test
  void approvedIsTerminal() {
    Manuscript manuscript = manuscriptAt(ManuscriptStage.APPROVED);
    when(manuscripts.findByIdAndOrganizationId(eq(manuscript.getId()), eq(orgId)))
        .thenReturn(Optional.of(manuscript));

    ApiException ex = assertThrows(ApiException.class,
        () -> service.transition(manuscript.getId(), ManuscriptStage.EDITING, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  void createWithForeignBookReturns404() {
    UUID foreignBook = UUID.randomUUID();
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(books.findByIdAndOrganizationId(eq(foreignBook), eq(orgId))).thenReturn(Optional.empty());

    CreateManuscriptRequest req = new CreateManuscriptRequest();
    req.setTitle("Linked");
    req.setBookId(foreignBook);

    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  @Test
  void updateBumpsVersionOnlyOnContentChange() {
    Manuscript manuscript = manuscriptAt(ManuscriptStage.EDITING);
    manuscript.setTitle("Old title");
    manuscript.setVersion(3);
    when(manuscripts.findByIdAndOrganizationId(eq(manuscript.getId()), eq(orgId)))
        .thenReturn(Optional.of(manuscript));
    when(manuscripts.save(any(Manuscript.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateManuscriptRequest titleChange = new UpdateManuscriptRequest();
    titleChange.setTitle("New title");
    assertEquals(4, service.update(manuscript.getId(), titleChange).getVersion());

    UpdateManuscriptRequest metaChange = new UpdateManuscriptRequest();
    metaChange.setDueDate(java.time.LocalDate.of(2026, 12, 1));
    assertEquals(4, service.update(manuscript.getId(), metaChange).getVersion());
  }

  private Manuscript manuscriptAt(ManuscriptStage stage) {
    Manuscript manuscript = new Manuscript();
    manuscript.setId(UUID.randomUUID());
    manuscript.setOrganization(org);
    manuscript.setTitle("Sample");
    manuscript.setStage(stage);
    manuscript.setVersion(1);
    return manuscript;
  }
}
