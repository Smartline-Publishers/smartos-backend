package com.smartline.smartos.modules.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookContributor;
import com.smartline.smartos.modules.book.BookContributorRepository;
import com.smartline.smartos.modules.book.Contributor;
import com.smartline.smartos.modules.finance.Contract.ContractStatus;
import com.smartline.smartos.modules.finance.RoyaltyDtos.GenerateStatementRequest;
import com.smartline.smartos.modules.finance.RoyaltyStatement.StatementStatus;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.sales.Order;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.OrderItem;
import com.smartline.smartos.modules.sales.OrderItemRepository;
import com.smartline.smartos.modules.sales.OrderRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class RoyaltyServiceTest {

  @Mock private RoyaltyStatementRepository statements;
  @Mock private ContractRepository contracts;
  @Mock private TransactionRepository transactions;
  @Mock private OrderRepository orders;
  @Mock private OrderItemRepository orderItems;
  @Mock private BookContributorRepository bookContributors;

  private RoyaltyService service;
  private UUID orgId;
  private Organization org;
  private UUID contributorId;
  private Book book;
  private Contract contract;

  @BeforeEach
  void setUp() {
    service = new RoyaltyService(statements, contracts, transactions, orders, orderItems,
        bookContributors);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    contributorId = UUID.randomUUID();
    Contributor contributor = new Contributor();
    contributor.setId(contributorId);
    contributor.setOrganization(org);
    contributor.setFullName("Ama Serwaa");
    book = new Book();
    book.setId(UUID.randomUUID());
    book.setOrganization(org);
    book.setTitle("Maths 4");
    contract = new Contract();
    contract.setId(UUID.randomUUID());
    contract.setOrganization(org);
    contract.setContributor(contributor);
    contract.setRoyaltyRate(new BigDecimal("0.1000"));
    contract.setStatus(ContractStatus.ACTIVE);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void generateComputesPayableAsNetTimesRate() {
    // Fixture: 10 × 100.00 sold, 100.00 refunded, 10% rate on a 100% share
    // → net 900.00, payable 90.00.
    LocalDate start = LocalDate.of(2026, 7, 1);
    LocalDate end = LocalDate.of(2026, 9, 30);
    Instant inPeriod = Instant.parse("2026-08-15T10:00:00Z");
    when(contracts.findByIdAndOrganizationId(eq(contract.getId()), eq(orgId)))
        .thenReturn(Optional.of(contract));
    when(bookContributors.findByContributorId(eq(contributorId)))
        .thenReturn(List.of(link(100.0)));
    Order order = fulfilledOrder(inPeriod);
    when(orders.findByOrganizationIdAndStatus(eq(orgId), eq(OrderStatus.FULFILLED)))
        .thenReturn(List.of(order));
    when(orderItems.findByOrderId(eq(order.getId())))
        .thenReturn(List.of(line(order, 10, new BigDecimal("100.00"))));
    when(transactions.findByOrganizationIdAndTypeAndCreatedAtBetween(eq(orgId),
        eq(TransactionType.REFUND), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(refund(order.getId(), new BigDecimal("100.00"), inPeriod)));
    when(statements.save(any(RoyaltyStatement.class))).thenAnswer(inv -> inv.getArgument(0));

    GenerateStatementRequest req = new GenerateStatementRequest();
    req.setContractId(contract.getId());
    req.setPeriodStart(start);
    req.setPeriodEnd(end);
    RoyaltyStatement statement = service.generate(req);

    assertEquals(StatementStatus.DRAFT, statement.getStatus());
    assertEquals(0, new BigDecimal("900.00").compareTo(statement.getNetSales()));
    assertEquals(0, new BigDecimal("0.1000").compareTo(statement.getRateSnapshot()));
    assertEquals(0, new BigDecimal("90.00").compareTo(statement.getPayable()));
  }

  @Test
  void approveMovesDraftForwardOnly() {
    RoyaltyStatement draft = statement(StatementStatus.DRAFT);
    when(statements.findByIdAndOrganizationId(eq(draft.getId()), eq(orgId)))
        .thenReturn(Optional.of(draft));
    when(statements.save(any(RoyaltyStatement.class))).thenAnswer(inv -> inv.getArgument(0));

    assertEquals(StatementStatus.APPROVED, service.approve(draft.getId()).getStatus());

    RoyaltyStatement approved = statement(StatementStatus.APPROVED);
    when(statements.findByIdAndOrganizationId(eq(approved.getId()), eq(orgId)))
        .thenReturn(Optional.of(approved));

    assertThrows(ApiException.class, () -> service.approve(approved.getId()));
  }

  @Test
  void payCreatesPayoutTransactionAtomically() {
    RoyaltyStatement approved = statement(StatementStatus.APPROVED);
    approved.setPayable(new BigDecimal("90.00"));
    when(statements.findByIdAndOrganizationId(eq(approved.getId()), eq(orgId)))
        .thenReturn(Optional.of(approved));
    when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    when(statements.save(any(RoyaltyStatement.class))).thenAnswer(inv -> inv.getArgument(0));

    RoyaltyStatement paid = service.pay(approved.getId());

    assertEquals(StatementStatus.PAID, paid.getStatus());
    ArgumentCaptor<Transaction> txn = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions).save(txn.capture());
    assertEquals(TransactionType.ROYALTY_PAYOUT, txn.getValue().getType());
    assertEquals(0, new BigDecimal("90.00").compareTo(txn.getValue().getAmount()));
    assertEquals(approved.getId(), txn.getValue().getReferenceId());
  }

  @Test
  void payDraftThrows400AndPostsNothing() {
    RoyaltyStatement draft = statement(StatementStatus.DRAFT);
    when(statements.findByIdAndOrganizationId(eq(draft.getId()), eq(orgId)))
        .thenReturn(Optional.of(draft));

    ApiException ex = assertThrows(ApiException.class, () -> service.pay(draft.getId()));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verify(transactions, never()).save(any());
  }

  @Test
  void generateOnTerminatedContractThrows400() {
    contract.setStatus(ContractStatus.TERMINATED);
    when(contracts.findByIdAndOrganizationId(eq(contract.getId()), eq(orgId)))
        .thenReturn(Optional.of(contract));

    GenerateStatementRequest req = new GenerateStatementRequest();
    req.setContractId(contract.getId());
    req.setPeriodStart(LocalDate.of(2026, 7, 1));
    req.setPeriodEnd(LocalDate.of(2026, 9, 30));

    assertThrows(ApiException.class, () -> service.generate(req));
  }

  private BookContributor link(double sharePct) {
    BookContributor link = new BookContributor();
    link.setId(UUID.randomUUID());
    link.setBook(book);
    link.setRoyaltyPct(sharePct);
    return link;
  }

  private Order fulfilledOrder(Instant updatedAt) {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setOrganization(org);
    order.setStatus(OrderStatus.FULFILLED);
    order.setTotal(new BigDecimal("1000.00"));
    order.setUpdatedAt(updatedAt);
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

  private Transaction refund(UUID orderId, BigDecimal amount, Instant at) {
    Transaction t = new Transaction();
    t.setId(UUID.randomUUID());
    t.setOrganization(org);
    t.setType(TransactionType.REFUND);
    t.setAmount(amount);
    t.setReferenceType("ORDER");
    t.setReferenceId(orderId);
    t.setCreatedAt(at);
    return t;
  }

  private RoyaltyStatement statement(StatementStatus status) {
    RoyaltyStatement s = new RoyaltyStatement();
    s.setId(UUID.randomUUID());
    s.setOrganization(org);
    s.setContract(contract);
    s.setStatus(status);
    return s;
  }
}
