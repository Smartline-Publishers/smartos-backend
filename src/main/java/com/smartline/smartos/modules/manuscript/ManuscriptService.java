package com.smartline.smartos.modules.manuscript;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.book.Contributor;
import com.smartline.smartos.modules.book.ContributorRepository;
import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.CreateManuscriptRequest;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.ManuscriptEventResponse;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.UpdateManuscriptRequest;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.AppUser;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManuscriptService {

  /**
   * Editorial stage machine. Backward moves allow rework; APPROVED is terminal.
   */
  static final Map<ManuscriptStage, Set<ManuscriptStage>> TRANSITIONS;

  static {
    Map<ManuscriptStage, Set<ManuscriptStage>> m = new EnumMap<>(ManuscriptStage.class);
    m.put(ManuscriptStage.SUBMITTED, EnumSet.of(ManuscriptStage.REVIEW));
    m.put(ManuscriptStage.REVIEW, EnumSet.of(ManuscriptStage.EDITING, ManuscriptStage.SUBMITTED));
    m.put(ManuscriptStage.EDITING, EnumSet.of(ManuscriptStage.PROOF, ManuscriptStage.REVIEW));
    m.put(ManuscriptStage.PROOF, EnumSet.of(ManuscriptStage.APPROVED, ManuscriptStage.EDITING));
    m.put(ManuscriptStage.APPROVED, EnumSet.noneOf(ManuscriptStage.class));
    TRANSITIONS = Map.copyOf(m);
  }

  private final ManuscriptRepository manuscripts;
  private final ManuscriptEventRepository events;
  private final OrganizationRepository organizations;
  private final BookRepository books;
  private final ContributorRepository contributors;
  private final AppUserRepository users;

  public ManuscriptService(ManuscriptRepository manuscripts, ManuscriptEventRepository events,
      OrganizationRepository organizations, BookRepository books,
      ContributorRepository contributors, AppUserRepository users) {
    this.manuscripts = manuscripts;
    this.events = events;
    this.organizations = organizations;
    this.books = books;
    this.contributors = contributors;
    this.users = users;
  }

  @Transactional
  @Audited(action = "manuscript.create", entityType = "Manuscript")
  public Manuscript create(CreateManuscriptRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    Manuscript manuscript = new Manuscript();
    manuscript.setOrganization(org);
    manuscript.setTitle(req.getTitle().trim());
    manuscript.setBook(resolveBook(orgId, req.getBookId()));
    manuscript.setContributor(resolveContributor(orgId, req.getContributorId()));
    manuscript.setAssignee(resolveAssignee(orgId, req.getAssigneeId()));
    manuscript.setDueDate(req.getDueDate());
    manuscript.setFileUrl(req.getFileUrl());
    manuscript.setStage(ManuscriptStage.SUBMITTED);
    manuscript.setVersion(1);
    Manuscript saved = manuscripts.save(manuscript);
    recordEvent(saved, null, ManuscriptStage.SUBMITTED, "Submitted");
    return saved;
  }

  @Transactional(readOnly = true)
  public Page<Manuscript> list(ManuscriptStage stage, String search, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (search != null && !search.isBlank()) {
      return manuscripts.findByOrganizationIdAndTitleContainingIgnoreCase(orgId, search.trim(),
          pageable);
    }
    if (stage != null) {
      return manuscripts.findByOrganizationIdAndStage(orgId, stage, pageable);
    }
    return manuscripts.findByOrganizationId(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public Manuscript get(UUID id) {
    UUID orgId = TenantContext.require();
    return manuscripts.findByIdAndOrganizationId(id, orgId)
        .orElseThrow(() -> ApiException.notFound("Manuscript", id));
  }

  @Transactional(readOnly = true)
  public List<ManuscriptEventResponse> history(UUID id) {
    Manuscript manuscript = get(id);
    return events.findTop50ByManuscriptIdOrderByCreatedAtDesc(manuscript.getId()).stream()
        .map(ManuscriptDtos.ManuscriptMapper::toResponse)
        .toList();
  }

  @Transactional
  @Audited(action = "manuscript.update", entityType = "Manuscript")
  public Manuscript update(UUID id, UpdateManuscriptRequest req) {
    Manuscript manuscript = get(id);
    UUID orgId = TenantContext.require();
    boolean contentChanged = false;
    if (req.getTitle() != null && !req.getTitle().isBlank()
        && !req.getTitle().trim().equals(manuscript.getTitle())) {
      manuscript.setTitle(req.getTitle().trim());
      contentChanged = true;
    }
    if (req.getFileUrl() != null && !Objects.equals(req.getFileUrl(), manuscript.getFileUrl())) {
      manuscript.setFileUrl(req.getFileUrl());
      contentChanged = true;
    }
    if (req.getContributorId() != null) {
      Contributor contributor = resolveContributor(orgId, req.getContributorId());
      UUID before = manuscript.getContributor() != null ? manuscript.getContributor().getId() : null;
      if (!Objects.equals(contributor.getId(), before)) {
        manuscript.setContributor(contributor);
        contentChanged = true;
      }
    }
    if (req.getBookId() != null) {
      manuscript.setBook(resolveBook(orgId, req.getBookId()));
    }
    if (req.getAssigneeId() != null) {
      manuscript.setAssignee(resolveAssignee(orgId, req.getAssigneeId()));
    }
    if (req.getDueDate() != null) {
      manuscript.setDueDate(req.getDueDate());
    }
    if (contentChanged) {
      manuscript.setVersion(manuscript.getVersion() + 1);
    }
    return manuscripts.save(manuscript);
  }

  @Transactional
  @Audited(action = "manuscript.transition", entityType = "Manuscript")
  public Manuscript transition(UUID id, ManuscriptStage toStage, String comment) {
    Manuscript manuscript = get(id);
    ManuscriptStage from = manuscript.getStage();
    if (from == toStage) {
      throw ApiException.badRequest("Manuscript is already in stage: " + toStage);
    }
    if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(toStage)) {
      throw ApiException.badRequest(
          "Cannot move manuscript from " + from + " to " + toStage);
    }
    manuscript.setStage(toStage);
    Manuscript saved = manuscripts.save(manuscript);
    recordEvent(saved, from, toStage, comment);
    return saved;
  }

  @Transactional
  @Audited(action = "manuscript.delete", entityType = "Manuscript")
  public void delete(UUID id) {
    manuscripts.delete(get(id));
  }

  private void recordEvent(Manuscript manuscript, ManuscriptStage from, ManuscriptStage to,
      String comment) {
    ManuscriptEvent event = new ManuscriptEvent();
    event.setManuscript(manuscript);
    event.setFromStage(from);
    event.setToStage(to);
    event.setActorId(currentActorId());
    event.setComment(comment);
    events.save(event);
  }

  private UUID currentActorId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      return principal.getUserId();
    }
    return null;
  }

  private Book resolveBook(UUID orgId, UUID bookId) {
    if (bookId == null) {
      return null;
    }
    return books.findByIdAndOrganizationId(bookId, orgId)
        .orElseThrow(() -> ApiException.notFound("Book", bookId));
  }

  private Contributor resolveContributor(UUID orgId, UUID contributorId) {
    if (contributorId == null) {
      return null;
    }
    return contributors.findByIdAndOrganizationId(contributorId, orgId)
        .orElseThrow(() -> ApiException.notFound("Contributor", contributorId));
  }

  private AppUser resolveAssignee(UUID orgId, UUID assigneeId) {
    if (assigneeId == null) {
      return null;
    }
    return users.findByIdAndOrganizationId(assigneeId, orgId)
        .orElseThrow(() -> ApiException.notFound("User", assigneeId));
  }
}
