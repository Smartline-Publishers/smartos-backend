package com.smartline.smartos.modules.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.finance.Contract;
import com.smartline.smartos.modules.finance.ContractRepository;
import com.smartline.smartos.modules.finance.RoyaltyStatement;
import com.smartline.smartos.modules.finance.RoyaltyStatement.StatementStatus;
import com.smartline.smartos.modules.finance.RoyaltyStatementRepository;
import com.smartline.smartos.modules.finance.Transaction;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.finance.TransactionRepository;
import com.smartline.smartos.modules.manuscript.Manuscript;
import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import com.smartline.smartos.modules.manuscript.ManuscriptEvent;
import com.smartline.smartos.modules.manuscript.ManuscriptEventRepository;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.reporting.ReportingDtos.EditorialReport;
import com.smartline.smartos.modules.reporting.ReportingDtos.RoyaltyLiability;
import com.smartline.smartos.modules.reporting.ReportingDtos.SalesReport;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

  @Mock private OrderRepository orders;
  @Mock private OrderItemRepository orderItems;
  @Mock private TransactionRepository transactions;
  @Mock private RoyaltyStatementRepository statements;
  @Mock private ContractRepository contracts;
  @Mock private ManuscriptRepository manuscripts;
  @Mock private ManuscriptEventRepository events;

  private ReportingService service;
  private UUID orgId;
  private Organization org;
  private Book bookA;
  private Book bookB;

  @BeforeEach
  void setUp() {
    service = new ReportingService(orders, orderItems, transactions, statements, contracts,
        manuscripts, events);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    bookA = book("Maths 4");
    bookB = book("Akan Tales");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void salesReportSplitsRevenueAndRefundsPerBook() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 9, 30);
    Instant inPeriod = Instant.parse("2026-08-10T10:00:00Z");
    Order order = fulfilledOrder(inPeriod);
    when(orders.findByOrganization_IdAndStatus(eq(orgId), eq(OrderStatus.FULFILLED)))
        .thenReturn(List.of(order));
    when(orderItems.findByOrderId(eq(order.getId()))).thenReturn(List.of(
        line(order, bookA, 2, new BigDecimal("100.00")),
        line(order, bookB, 1, new BigDecimal("50.00"))));
    when(transactions.findByOrganization_IdAndTypeAndCreatedAtBetween(eq(orgId),
        eq(TransactionType.REFUND), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(refund(order.getId(), new BigDecimal("30.00"), inPeriod)));

    SalesReport report = service.salesReport(from, to, null);

    // Revenue 250, refund split pro-rata 24/6 → nets 176 and 44.
    assertEquals(2, report.getRows().size());
    assertEquals(3, report.getTotalQuantity());
    assertEquals(0, new BigDecimal("250.00").compareTo(report.getTotalRevenue()));
    assertEquals(0, new BigDecimal("30.00").compareTo(report.getTotalRefunds()));
    assertEquals(0, new BigDecimal("220.00").compareTo(report.getTotalNet()));
  }

  @Test
  void salesCsvTotalsMatchApiTotals() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 9, 30);
    Order order = fulfilledOrder(Instant.parse("2026-08-10T10:00:00Z"));
    when(orders.findByOrganization_IdAndStatus(eq(orgId), eq(OrderStatus.FULFILLED)))
        .thenReturn(List.of(order));
    when(orderItems.findByOrderId(eq(order.getId())))
        .thenReturn(List.of(line(order, bookA, 2, new BigDecimal("100.00"))));
    when(transactions.findByOrganization_IdAndTypeAndCreatedAtBetween(eq(orgId),
        eq(TransactionType.REFUND), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of());

    SalesReport report = service.salesReport(from, to, null);
    String csv = service.salesCsv(report);
    String[] lines = csv.split("\n");

    assertEquals("book_id,book_title,quantity,revenue,refunds,net", lines[0]);
    String total = lines[lines.length - 1];
    assertTrue(total.startsWith("TOTAL,," + report.getTotalQuantity() + ","
        + report.getTotalRevenue() + "," + report.getTotalRefunds() + ","
        + report.getTotalNet()));
  }

  @Test
  void royaltyLiabilitySplitsPendingFromPaid() {
    Contract contract = new Contract();
    contract.setId(UUID.randomUUID());
    RoyaltyStatement draft = statement(contract, StatementStatus.DRAFT, new BigDecimal("100.00"));
    RoyaltyStatement approved = statement(contract, StatementStatus.APPROVED,
        new BigDecimal("50.00"));
    RoyaltyStatement paid = statement(contract, StatementStatus.PAID, new BigDecimal("1000.00"));
    when(statements.findByOrganization_Id(eq(orgId), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(draft, approved, paid)));
    when(contracts.findByOrganization_Id(eq(orgId))).thenReturn(List.of());

    RoyaltyLiability liability = service.royaltyLiability();

    assertEquals(0, new BigDecimal("100.00").compareTo(liability.getDraftTotal()));
    assertEquals(0, new BigDecimal("50.00").compareTo(liability.getApprovedTotal()));
    assertEquals(0, new BigDecimal("1000.00").compareTo(liability.getPaidTotal()));
    assertEquals(0, new BigDecimal("150.00").compareTo(
        liability.getByContributor().get(0).getPending()));
  }

  @Test
  void editorialReportCountsWindowedTransitions() {
    Manuscript manuscript = new Manuscript();
    manuscript.setId(UUID.randomUUID());
    manuscript.setOrganization(org);
    manuscript.setTitle("Ga Proverbs");
    manuscript.setStage(ManuscriptStage.REVIEW);
    manuscript.setCreatedAt(Instant.now());
    when(manuscripts.findByOrganization_Id(eq(orgId), eq(Pageable.unpaged())))
        .thenReturn(new PageImpl<>(List.of(manuscript)));
    ManuscriptEvent toReview = event(manuscript, null, ManuscriptStage.REVIEW);
    ManuscriptEvent toApproved = event(manuscript, ManuscriptStage.PROOF, ManuscriptStage.APPROVED);
    when(events.findByManuscriptIdAndCreatedAtBetween(eq(manuscript.getId()),
        any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(toReview, toApproved));

    EditorialReport report = service.editorialReport(30);

    assertEquals(1, report.getSubmitted());
    assertEquals(1, report.getApproved());
    assertEquals(2, report.getTransitions().size());
  }

  private Book book(String title) {
    Book book = new Book();
    book.setId(UUID.randomUUID());
    book.setOrganization(org);
    book.setTitle(title);
    return book;
  }

  private Order fulfilledOrder(Instant updatedAt) {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setOrganization(org);
    order.setStatus(OrderStatus.FULFILLED);
    order.setTotal(new BigDecimal("250.00"));
    order.setUpdatedAt(updatedAt);
    return order;
  }

  private OrderItem line(Order order, Book book, int quantity, BigDecimal price) {
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

  private RoyaltyStatement statement(Contract contract, StatementStatus status, BigDecimal payable) {
    RoyaltyStatement s = new RoyaltyStatement();
    s.setId(UUID.randomUUID());
    s.setOrganization(org);
    s.setContract(contract);
    s.setStatus(status);
    s.setPayable(payable);
    return s;
  }

  private ManuscriptEvent event(Manuscript manuscript, ManuscriptStage from, ManuscriptStage to) {
    ManuscriptEvent e = new ManuscriptEvent();
    e.setId(UUID.randomUUID());
    e.setManuscript(manuscript);
    e.setFromStage(from);
    e.setToStage(to);
    e.setCreatedAt(Instant.now());
    return e;
  }
}
