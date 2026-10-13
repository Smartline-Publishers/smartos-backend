package com.smartline.smartos.modules.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.finance.Transaction;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.finance.TransactionRepository;
import com.smartline.smartos.modules.inventory.InventoryService;
import com.smartline.smartos.modules.inventory.StockMovement.MovementType;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.SalesDtos.CreateOrderRequest;
import com.smartline.smartos.modules.sales.SalesDtos.OrderItemRequest;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orders;
  @Mock private OrderItemRepository items;
  @Mock private CustomerRepository customers;
  @Mock private BookRepository books;
  @Mock private OrganizationRepository organizations;
  @Mock private InventoryService inventory;
  @Mock private TransactionRepository transactions;

  private OrderService service;
  private UUID orgId;
  private Organization org;
  private Customer customer;
  private Book book;

  @BeforeEach
  void setUp() {
    service = new OrderService(orders, items, customers, books, organizations, inventory,
        transactions);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    customer = new Customer();
    customer.setId(UUID.randomUUID());
    customer.setOrganization(org);
    customer.setName("Ridge School");
    book = new Book();
    book.setId(UUID.randomUUID());
    book.setOrganization(org);
    book.setTitle("Maths 4");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createComputesTotalServerSide() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(customers.findByIdAndOrganizationId(eq(customer.getId()), eq(orgId)))
        .thenReturn(Optional.of(customer));
    when(books.findByIdAndOrganizationId(eq(book.getId()), eq(orgId)))
        .thenReturn(Optional.of(book));
    when(orders.save(any(Order.class))).thenAnswer(inv -> {
      Order o = inv.getArgument(0);
      if (o.getId() == null) {
        o.setId(UUID.randomUUID());
      }
      return o;
    });
    when(items.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateOrderRequest req = new CreateOrderRequest();
    req.setCustomerId(customer.getId());
    req.setItems(List.of(item(2, "15.00"), item(1, "9.99")));

    // There is no total field on the request: client math cannot reach the total.
    Order saved = service.create(req);

    assertEquals(0, new BigDecimal("39.99").compareTo(saved.getTotal()));
    assertEquals(OrderStatus.DRAFT, saved.getStatus());
  }

  @Test
  void fulfilPostsMovementsAndSaleAtomically() {
    Order order = confirmedOrder(new BigDecimal("100.00"));
    OrderItem line = line(order, 2, new BigDecimal("50.00"));
    UUID warehouseId = UUID.randomUUID();
    when(orders.findByIdAndOrganizationId(eq(order.getId()), eq(orgId)))
        .thenReturn(Optional.of(order));
    when(items.findByOrderId(eq(order.getId()))).thenReturn(List.of(line));
    when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order fulfilled = service.fulfil(order.getId(), warehouseId);

    assertEquals(OrderStatus.FULFILLED, fulfilled.getStatus());
    verify(inventory).recordMovement(eq(book.getId()), eq(warehouseId), eq(-2),
        eq(MovementType.OUT), eq("ORDER"), eq(order.getId()), any());
    ArgumentCaptor<Transaction> txn = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions).save(txn.capture());
    assertEquals(TransactionType.SALE, txn.getValue().getType());
    assertEquals(0, new BigDecimal("100.00").compareTo(txn.getValue().getAmount()));
    assertEquals(order.getId(), txn.getValue().getReferenceId());
  }

  @Test
  void fulfilShortfallRollsBackSale() {
    Order order = confirmedOrder(new BigDecimal("100.00"));
    OrderItem line = line(order, 200, new BigDecimal("0.50"));
    UUID warehouseId = UUID.randomUUID();
    when(orders.findByIdAndOrganizationId(eq(order.getId()), eq(orgId)))
        .thenReturn(Optional.of(order));
    when(items.findByOrderId(eq(order.getId()))).thenReturn(List.of(line));
    when(inventory.recordMovement(eq(book.getId()), eq(warehouseId), eq(-200),
        eq(MovementType.OUT), eq("ORDER"), eq(order.getId()), any()))
        .thenThrow(ApiException.conflict("Insufficient stock"));

    ApiException ex = assertThrows(ApiException.class,
        () -> service.fulfil(order.getId(), warehouseId));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    verify(transactions, never()).save(any());
    verify(orders, never()).save(any());
    assertEquals(OrderStatus.CONFIRMED, order.getStatus());
  }

  @Test
  void fulfilDraftOrderThrows400() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setOrganization(org);
    order.setCustomer(customer);
    order.setStatus(OrderStatus.DRAFT);
    when(orders.findByIdAndOrganizationId(eq(order.getId()), eq(orgId)))
        .thenReturn(Optional.of(order));

    assertThrows(ApiException.class, () -> service.fulfil(order.getId(), UUID.randomUUID()));
  }

  @Test
  void confirmEmptyOrderThrows400() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setOrganization(org);
    order.setCustomer(customer);
    order.setStatus(OrderStatus.DRAFT);
    when(orders.findByIdAndOrganizationId(eq(order.getId()), eq(orgId)))
        .thenReturn(Optional.of(order));
    when(items.findByOrderId(eq(order.getId()))).thenReturn(List.of());

    assertThrows(ApiException.class, () -> service.confirm(order.getId()));
  }

  private OrderItemRequest item(int quantity, String price) {
    OrderItemRequest r = new OrderItemRequest();
    r.setBookId(book.getId());
    r.setQuantity(quantity);
    r.setUnitPrice(new BigDecimal(price));
    return r;
  }

  private Order confirmedOrder(BigDecimal total) {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setOrganization(org);
    order.setCustomer(customer);
    order.setStatus(OrderStatus.CONFIRMED);
    order.setTotal(total);
    return order;
  }

  private OrderItem line(Order order, int quantity, BigDecimal price) {
    OrderItem item = new OrderItem();
    item.setId(UUID.randomUUID());
    item.setOrder(order);
    item.setBook(book);
    item.setQuantity(quantity);
    item.setUnitPrice(price);
    return item;
  }
}
