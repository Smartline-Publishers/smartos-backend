package com.smartline.smartos.modules.book;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.Book.BookStatus;
import com.smartline.smartos.modules.book.BookDtos.CreateBookRequest;
import com.smartline.smartos.modules.book.BookDtos.UpdateBookRequest;
import com.smartline.smartos.modules.inventory.StockMovementRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
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

  public BookService(BookRepository books, OrganizationRepository organizations,
      StockMovementRepository movements) {
    this.books = books;
    this.organizations = organizations;
    this.movements = movements;
  }

  @Transactional
  @Audited(action = "book.create", entityType = "Book")
  public Book create(CreateBookRequest req) {
    UUID orgId = TenantContext.require();
    if (req.getIsbn() != null && !req.getIsbn().isBlank()
        && books.existsByOrganizationIdAndIsbn(orgId, req.getIsbn().trim())) {
      throw ApiException.conflict("ISBN already exists in organization: " + req.getIsbn());
    }
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
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
      return books.findByOrganizationIdAndStatusAndTitleContainingIgnoreCase(
          orgId, status, search.trim(), pageable);
    }
    if (status != null) {
      return books.findByOrganizationIdAndStatus(orgId, status, pageable);
    }
    if (search != null && !search.isBlank()) {
      return books.findByOrganizationIdAndTitleContainingIgnoreCase(orgId, search.trim(), pageable);
    }
    return books.findByOrganizationId(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public Book get(UUID id) {
    UUID orgId = TenantContext.require();
    return books.findByIdAndOrganizationId(id, orgId)
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
        && books.existsByOrganizationIdAndIsbn(orgId, req.getIsbn().trim())) {
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
    if (movements.existsByOrganizationIdAndBookId(orgId, book.getId())) {
      throw ApiException.conflict("Book has inventory history and cannot be deleted");
    }
    books.delete(book);
  }
}
