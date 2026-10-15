package com.smartline.smartos.modules.book;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.Book.BookStatus;
import com.smartline.smartos.modules.book.BookDtos.BookLinkResponse;
import com.smartline.smartos.modules.book.BookDtos.CreateBookRequest;
import com.smartline.smartos.modules.book.BookDtos.LinkContributorRequest;
import com.smartline.smartos.modules.book.BookDtos.UpdateBookRequest;
import com.smartline.smartos.modules.inventory.StockMovementRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

  private final BookRepository books;
  private final OrganizationRepository organizations;
  private final StockMovementRepository movements;
  private final BookContributorRepository links;
  private final ContributorRepository contributors;

  public BookService(BookRepository books, OrganizationRepository organizations,
      StockMovementRepository movements, BookContributorRepository links,
      ContributorRepository contributors) {
    this.books = books;
    this.organizations = organizations;
    this.movements = movements;
    this.links = links;
    this.contributors = contributors;
  }

  @Transactional
  @Audited(action = "book.create", entityType = "Book")
  public Book create(CreateBookRequest req) {
    UUID orgId = TenantContext.require();
    if (req.getIsbn() != null && !req.getIsbn().isBlank()
        && books.existsByOrganization_IdAndIsbn(orgId, req.getIsbn().trim())) {
      throw ApiException.conflict("ISBN already exists in organization: " + req.getIsbn());
    }
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    if (books.countByOrganization_Id(orgId) >= org.getPlan().getMaxBooks()) {
      throw ApiException.planLimit("Organization book quota reached for plan "
          + org.getPlan().name());
    }
    Book book = new Book();
    book.setOrganization(org);
    book.setTitle(req.getTitle().trim());
    book.setSubtitle(req.getSubtitle());
    book.setIsbn(req.getIsbn() != null ? req.getIsbn().trim() : null);
    book.setImprint(req.getImprint());
    book.setStatus(req.getStatus() != null ? req.getStatus() : BookStatus.DRAFT);
    book.setLanguage(req.getLanguage());
    book.setPageCount(req.getPageCount());
    return books.save(book);
  }

  @Transactional(readOnly = true)
  public Page<Book> list(BookStatus status, String search, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (status != null && search != null && !search.isBlank()) {
      return books.findByOrganization_IdAndStatusAndTitleContainingIgnoreCase(
          orgId, status, search.trim(), pageable);
    }
    if (status != null) {
      return books.findByOrganization_IdAndStatus(orgId, status, pageable);
    }
    if (search != null && !search.isBlank()) {
      return books.findByOrganization_IdAndTitleContainingIgnoreCase(orgId, search.trim(), pageable);
    }
    return books.findByOrganization_Id(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public Book get(UUID id) {
    UUID orgId = TenantContext.require();
    return books.findByIdAndOrganization_Id(id, orgId)
        .orElseThrow(() -> ApiException.notFound("Book", id));
  }

  @Transactional
  @Audited(action = "book.update", entityType = "Book")
  public Book update(UUID id, UpdateBookRequest req) {
    Book book = get(id);
    UUID orgId = TenantContext.require();
    if (req.getTitle() != null && !req.getTitle().isBlank()) {
      book.setTitle(req.getTitle().trim());
    }
    if (req.getSubtitle() != null) {
      book.setSubtitle(req.getSubtitle());
    }
    if (req.getIsbn() != null && !req.getIsbn().isBlank()
        && !req.getIsbn().trim().equals(book.getIsbn())
        && books.existsByOrganization_IdAndIsbn(orgId, req.getIsbn().trim())) {
      throw ApiException.conflict("ISBN already exists in organization: " + req.getIsbn());
    } else if (req.getIsbn() != null) {
      book.setIsbn(req.getIsbn().trim());
    }
    if (req.getImprint() != null) {
      book.setImprint(req.getImprint());
    }
    if (req.getStatus() != null) {
      book.setStatus(req.getStatus());
    }
    if (req.getLanguage() != null) {
      book.setLanguage(req.getLanguage());
    }
    if (req.getPageCount() != null) {
      book.setPageCount(req.getPageCount());
    }
    return books.save(book);
  }

  @Transactional
  @Audited(action = "book.delete", entityType = "Book")
  public void delete(UUID id) {
    UUID orgId = TenantContext.require();
    Book book = get(id);
    if (movements.existsByOrganization_IdAndBookId(orgId, book.getId())) {
      throw ApiException.conflict("Book has inventory history and cannot be deleted");
    }
    books.delete(book);
  }

  @Transactional(readOnly = true)
  public List<BookLinkResponse> links(UUID bookId) {
    Book book = get(bookId);
    List<BookLinkResponse> result = new ArrayList<>();
    for (BookContributor link : links.findByBookId(book.getId())) {
      Contributor contributor = link.getContributor();
      String fullName = contributor != null ? contributor.getFullName() : null;
      if (fullName == null && contributor != null) {
        fullName = contributors.findByIdAndOrganization_Id(contributor.getId(),
                TenantContext.require())
            .map(Contributor::getFullName)
            .orElse(null);
      }
      result.add(BookDtos.BookMapper.toLinkResponse(link, fullName));
    }
    return result;
  }

  @Transactional
  @Audited(action = "book.contributor.link", entityType = "BookContributor")
  public BookLinkResponse linkContributor(UUID bookId, LinkContributorRequest req) {
    UUID orgId = TenantContext.require();
    Book book = get(bookId);
    Contributor contributor = contributors.findByIdAndOrganization_Id(req.getContributorId(), orgId)
        .orElseThrow(() -> ApiException.notFound("Contributor", req.getContributorId()));
    if (links.existsByBookIdAndContributorId(book.getId(), contributor.getId())) {
      throw ApiException.conflict("Contributor is already linked to this book");
    }
    BookContributor link = new BookContributor();
    link.setBook(book);
    link.setContributor(contributor);
    link.setRole(req.getRole() != null ? req.getRole() : "AUTHOR");
    link.setRoyaltyPct(req.getRoyaltyPct());
    BookContributor saved = links.save(link);
    return BookDtos.BookMapper.toLinkResponse(saved, contributor.getFullName());
  }

  @Transactional
  @Audited(action = "book.contributor.unlink", entityType = "BookContributor")
  public void unlinkContributor(UUID bookId, UUID contributorId) {
    Book book = get(bookId);
    if (!links.existsByBookIdAndContributorId(book.getId(), contributorId)) {
      throw ApiException.notFound("BookContributor", contributorId);
    }
    links.deleteByBookIdAndContributorId(book.getId(), contributorId);
  }
}
