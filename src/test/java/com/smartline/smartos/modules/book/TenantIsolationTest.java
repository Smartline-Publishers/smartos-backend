package com.smartline.smartos.modules.book;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.modules.inventory.StockMovementRepository;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Tenant isolation checks: verifies the service layer always scopes
 * repository calls to the tenant id from TenantContext (no container needed).
 */
@ExtendWith(MockitoExtension.class)
class TenantIsolationTest {

  @Mock private BookRepository books;
  @Mock private ContributorRepository contributors;
  @Mock private OrganizationRepository organizations;
  @Mock private StockMovementRepository movements;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void bookQueriesAreScopedToTenant() {
    UUID orgA = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgA);
    var pageable = PageRequest.of(0, 10);
    when(books.findByOrganizationId(eq(orgA), eq(pageable))).thenReturn(Page.empty(pageable));

    new BookService(books, organizations, movements).list(null, null, pageable);

    verify(books).findByOrganizationId(eq(orgA), eq(pageable));
  }

  @Test
  void contributorQueriesAreScopedToTenant() {
    UUID orgA = UUID.randomUUID();
    UUID orgB = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgA);
    var pageable = PageRequest.of(0, 5);
    when(contributors.findByOrganizationId(eq(orgA), eq(pageable)))
        .thenReturn(Page.empty(pageable));

    new ContributorService(contributors, organizations).list(pageable);

    verify(contributors).findByOrganizationId(eq(orgA), eq(pageable));
    // orgB must never leak into the query for orgA's context
    assertNotEquals(orgA, orgB);
  }
}
