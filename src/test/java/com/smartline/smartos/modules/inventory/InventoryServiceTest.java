package com.smartline.smartos.modules.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.inventory.InventoryDtos.CreateMovementRequest;
import com.smartline.smartos.modules.inventory.InventoryDtos.CreateWarehouseRequest;
import com.smartline.smartos.modules.inventory.InventoryDtos.StockLevelResponse;
import com.smartline.smartos.modules.inventory.InventoryDtos.TransferRequest;
import com.smartline.smartos.modules.inventory.StockMovement.MovementType;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.util.List;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

  @Mock private StockMovementRepository movements;
  @Mock private WarehouseRepository warehouses;
  @Mock private BookRepository books;
  @Mock private OrganizationRepository organizations;

  private InventoryService service;
  private UUID orgId;
  private Organization org;
  private Book book;
  private Warehouse warehouse;

  @BeforeEach
  void setUp() {
    service = new InventoryService(movements, warehouses, books, organizations);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    book = new Book();
    book.setId(UUID.randomUUID());
    book.setOrganization(org);
    book.setTitle("Maths 4");
    warehouse = new Warehouse();
    warehouse.setId(UUID.randomUUID());
    warehouse.setOrganization(org);
    warehouse.setName("Accra");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void warehouseNameUniquePerOrg() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(warehouses.existsByOrganization_IdAndName(eq(orgId), eq("Accra"))).thenReturn(true);

    CreateWarehouseRequest req = new CreateWarehouseRequest();
    req.setName("Accra");

    ApiException ex = assertThrows(ApiException.class, () -> service.createWarehouse(req));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
  }

  @Test
  void transferCreatesPairedMovementsSharingReference() {
    Warehouse target = new Warehouse();
    target.setId(UUID.randomUUID());
    target.setOrganization(org);
    target.setName("Kumasi");
    when(books.findByIdAndOrganization_Id(eq(book.getId()), eq(orgId)))
        .thenReturn(Optional.of(book));
    when(warehouses.findByIdAndOrganization_Id(eq(warehouse.getId()), eq(orgId)))
        .thenReturn(Optional.of(warehouse));
    when(warehouses.findByIdAndOrganization_Id(eq(target.getId()), eq(orgId)))
        .thenReturn(Optional.of(target));
    when(movements.findByOrganization_Id(eq(orgId)))
        .thenReturn(List.of(movement(book, warehouse, 100)));
    when(movements.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

    TransferRequest req = new TransferRequest();
    req.setBookId(book.getId());
    req.setFromWarehouseId(warehouse.getId());
    req.setToWarehouseId(target.getId());
    req.setQuantity(40);
    List<StockMovement> legs = service.transfer(req);

    assertEquals(2, legs.size());
    ArgumentCaptor<StockMovement> saved = ArgumentCaptor.forClass(StockMovement.class);
    verify(movements, times(2)).save(saved.capture());
    // Both transfer legs share one save call site; assert the pair via all values.
    List<StockMovement> all = saved.getAllValues();
    assertEquals(2, all.size());
    assertEquals(-40, all.get(0).getQtyDelta());
    assertEquals(MovementType.OUT, all.get(0).getType());
    assertEquals(40, all.get(1).getQtyDelta());
    assertEquals(MovementType.IN, all.get(1).getType());
    assertEquals("TRANSFER", all.get(0).getReferenceType());
    assertEquals(all.get(0).getReferenceId(), all.get(1).getReferenceId());
  }

  @Test
  void transferExceedingStockThrows409AndSavesNothing() {
    Warehouse target = new Warehouse();
    target.setId(UUID.randomUUID());
    target.setOrganization(org);
    target.setName("Kumasi");
    when(books.findByIdAndOrganization_Id(eq(book.getId()), eq(orgId)))
        .thenReturn(Optional.of(book));
    when(warehouses.findByIdAndOrganization_Id(eq(warehouse.getId()), eq(orgId)))
        .thenReturn(Optional.of(warehouse));
    // No to-warehouse stub: the OUT leg fails the stock check first.
    when(movements.findByOrganization_Id(eq(orgId))).thenReturn(List.of());

    TransferRequest req = new TransferRequest();
    req.setBookId(book.getId());
    req.setFromWarehouseId(warehouse.getId());
    req.setToWarehouseId(target.getId());
    req.setQuantity(40);

    ApiException ex = assertThrows(ApiException.class, () -> service.transfer(req));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    verify(movements, never()).save(any());
  }

  @Test
  void outMovementBelowZeroBlocked() {
    when(books.findByIdAndOrganization_Id(eq(book.getId()), eq(orgId)))
        .thenReturn(Optional.of(book));
    when(warehouses.findByIdAndOrganization_Id(eq(warehouse.getId()), eq(orgId)))
        .thenReturn(Optional.of(warehouse));
    when(movements.findByOrganization_Id(eq(orgId))).thenReturn(List.of());

    CreateMovementRequest req = new CreateMovementRequest();
    req.setBookId(book.getId());
    req.setWarehouseId(warehouse.getId());
    req.setQtyDelta(-5);
    req.setType(MovementType.OUT);

    assertEquals(HttpStatus.CONFLICT,
        assertThrows(ApiException.class, () -> service.recordMovement(req)).getStatus());
  }

  @Test
  void adjustmentByAdminAllowedBelowZero() {
    authenticate(Role.ORG_ADMIN);
    when(books.findByIdAndOrganization_Id(eq(book.getId()), eq(orgId)))
        .thenReturn(Optional.of(book));
    when(warehouses.findByIdAndOrganization_Id(eq(warehouse.getId()), eq(orgId)))
        .thenReturn(Optional.of(warehouse));
    when(movements.findByOrganization_Id(eq(orgId))).thenReturn(List.of());
    when(movements.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateMovementRequest req = new CreateMovementRequest();
    req.setBookId(book.getId());
    req.setWarehouseId(warehouse.getId());
    req.setQtyDelta(-5);
    req.setType(MovementType.ADJUSTMENT);

    assertEquals(-5, service.recordMovement(req).getQtyDelta());
  }

  @Test
  void levelsAggregateSumsAndBelowFilter() {
    Book other = new Book();
    other.setId(UUID.randomUUID());
    other.setOrganization(org);
    other.setTitle("Akan Tales");
    when(movements.findByOrganization_Id(eq(orgId))).thenReturn(List.of(
        movement(book, warehouse, 10),
        movement(book, warehouse, -3),
        movement(other, warehouse, 5)));
    when(books.findAllById(any())).thenReturn(List.of(book, other));
    when(warehouses.findAllById(any())).thenReturn(List.of(warehouse));

    List<StockLevelResponse> all = service.levels(null, null, null);
    assertEquals(2, all.size());

    List<StockLevelResponse> low = service.levels(null, null, 6);
    assertEquals(1, low.size());
    assertEquals(other.getId(), low.get(0).getBookId());
    assertEquals(5, low.get(0).getQuantity());
  }

  private StockMovement movement(Book b, Warehouse w, int delta) {
    StockMovement m = new StockMovement();
    m.setId(UUID.randomUUID());
    m.setOrganization(org);
    m.setBook(b);
    m.setWarehouse(w);
    m.setQtyDelta(delta);
    m.setType(delta >= 0 ? MovementType.IN : MovementType.OUT);
    return m;
  }

  private void authenticate(Role role) {
    UserPrincipal principal =
        new UserPrincipal(UUID.randomUUID(), orgId, List.of(role.name()));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }
}
