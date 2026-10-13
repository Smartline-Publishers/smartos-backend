package com.smartline.smartos.modules.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.book.BookDtos.CreateBookRequest;
import com.smartline.smartos.modules.inventory.StockMovementRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

  @Mock private BookRepository books;
  @Mock private OrganizationRepository organizations;
  @Mock private StockMovementRepository movements;

  private BookService service;
  private UUID orgId;

  @BeforeEach
  void setUp() {
    service = new BookService(books, organizations, movements);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void duplicateIsbnThrows409() {
    when(books.existsByOrganizationIdAndIsbn(eq(orgId), eq("978-0-00"))).thenReturn(true);
    CreateBookRequest req = new CreateBookRequest("Title", null, "978-0-00", null, null, "en", 100);
    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));
    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
  }

  @Test
  void tenantScopingPassesOrgIdToRepo() {
    Organization org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    // No existsByOrganizationIdAndIsbn stub: req.isbn is null so create() skips the check.
    when(books.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateBookRequest req = new CreateBookRequest("Tenant Book", null, null, null, null, "en", 10);
    Book saved = service.create(req);

    verify(books).save(any(Book.class));
    assertEquals(orgId, saved.getOrganization().getId());
  }

  @Test
  void deleteBlockedWhenStockHistoryExists() {
    Book book = new Book();
    book.setId(UUID.randomUUID());
    when(books.findByIdAndOrganizationId(eq(book.getId()), eq(orgId)))
        .thenReturn(Optional.of(book));
    when(movements.existsByOrganizationIdAndBookId(eq(orgId), eq(book.getId())))
        .thenReturn(true);

    ApiException ex = assertThrows(ApiException.class, () -> service.delete(book.getId()));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    verify(books, never()).delete(any());
  }

  @Test
  void deleteWithoutHistoryRemovesBook() {
    Book book = new Book();
    book.setId(UUID.randomUUID());
    when(books.findByIdAndOrganizationId(eq(book.getId()), eq(orgId)))
        .thenReturn(Optional.of(book));
    when(movements.existsByOrganizationIdAndBookId(eq(orgId), eq(book.getId())))
        .thenReturn(false);

    service.delete(book.getId());

    verify(books).delete(eq(book));
  }
}
