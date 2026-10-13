package com.smartline.smartos.modules.reporting;

import com.smartline.smartos.modules.finance.ContractRepository;
import com.smartline.smartos.modules.finance.RoyaltyStatement;
import com.smartline.smartos.modules.finance.RoyaltyStatementRepository;
import com.smartline.smartos.modules.finance.Transaction;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.finance.TransactionRepository;
import com.smartline.smartos.modules.manuscript.Manuscript;
import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import com.smartline.smartos.modules.manuscript.ManuscriptEvent;
import com.smartline.smartos.modules.manuscript.ManuscriptEventRepository;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.reporting.ReportingDtos.ContributorLiability;
import com.smartline.smartos.modules.reporting.ReportingDtos.EditorialReport;
import com.smartline.smartos.modules.reporting.ReportingDtos.RoyaltyLiability;
import com.smartline.smartos.modules.reporting.ReportingDtos.SalesReport;
import com.smartline.smartos.modules.reporting.ReportingDtos.SalesRow;
import com.smartline.smartos.modules.reporting.ReportingDtos.StageCount;
import com.smartline.smartos.modules.sales.Order;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.OrderItem;
import com.smartline.smartos.modules.sales.OrderItemRepository;
import com.smartline.smartos.modules.sales.OrderRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only aggregates across modules. Everything is computed from the
 * system-of-record tables on each call — reports never store their own
 * copies, so they cannot drift from the underlying data.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final TransactionRepository transactions;
  private final RoyaltyStatementRepository statements;
  private final ContractRepository contracts;
  private final ManuscriptRepository manuscripts;
  private final ManuscriptEventRepository events;

  public ReportingService(OrderRepository orders, OrderItemRepository orderItems,
      TransactionRepository transactions, RoyaltyStatementRepository statements,
      ContractRepository contracts, ManuscriptRepository manuscripts,
      ManuscriptEventRepository events) {
    this.orders = orders;
    this.orderItems = orderItems;
    this.transactions = transactions;
    this.statements = statements;
    this.contracts = contracts;
    this.manuscripts = manuscripts;
    this.events = events;
  }

  public SalesReport salesReport(LocalDate from, LocalDate to, UUID bookId) {
    UUID orgId = TenantContext.require();
    Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Map<UUID, SalesAccumulator> byBook = new HashMap<>();
    Map<UUID, String> titles = new HashMap<>();
    for (Order order : orders.findByOrganizationIdAndStatus(orgId, OrderStatus.FULFILLED)) {
      if (!inWindow(order.getUpdatedAt(), start, end)) {
        continue;
      }
      for (OrderItem item : orderItems.findByOrderId(order.getId())) {
        UUID b = item.getBook().getId();
        if (bookId != null && !bookId.equals(b)) {
          continue;
        }
        titles.putIfAbsent(b, item.getBook().getTitle());
        SalesAccumulator acc = byBook.computeIfAbsent(b, k -> new SalesAccumulator());
        acc.quantity += item.getQuantity();
        acc.revenue = acc.revenue.add(
            item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
      }
    }
    for (Transaction refund : transactions.findByOrganizationIdAndTypeAndCreatedAtBetween(orgId,
        TransactionType.REFUND, start, end)) {
      if (!"ORDER".equals(refund.getReferenceType()) || refund.getReferenceId() == null) {
        continue;
      }
      List<OrderItem> lines = orderItems.findByOrderId(refund.getReferenceId());
      BigDecimal orderTotal = lines.stream()
          .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (orderTotal.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      for (OrderItem item : lines) {
        UUID b = item.getBook().getId();
        if (bookId != null && !bookId.equals(b) || !byBook.containsKey(b)) {
          continue;
        }
        BigDecimal line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        BigDecimal share = line.divide(orderTotal, 10, RoundingMode.HALF_UP);
        byBook.get(b).refunds = byBook.get(b).refunds.add(refund.getAmount().multiply(share));
      }
    }
    List<SalesRow> rows = new ArrayList<>();
    int totalQuantity = 0;
    BigDecimal totalRevenue = BigDecimal.ZERO;
    BigDecimal totalRefunds = BigDecimal.ZERO;
    List<UUID> sorted = new ArrayList<>(byBook.keySet());
    sorted.sort(Comparator.comparing(b -> titles.getOrDefault(b, "?")));
    for (UUID b : sorted) {
      SalesAccumulator acc = byBook.get(b);
      BigDecimal revenue = acc.revenue.setScale(2, RoundingMode.HALF_UP);
      BigDecimal refunds = acc.refunds.setScale(2, RoundingMode.HALF_UP);
      BigDecimal net = revenue.subtract(refunds);
      rows.add(new SalesRow(b.toString(), titles.getOrDefault(b, "?"), acc.quantity, revenue,
          refunds, net));
      totalQuantity += acc.quantity;
      totalRevenue = totalRevenue.add(revenue);
      totalRefunds = totalRefunds.add(refunds);
    }
    BigDecimal totalNet = totalRevenue.subtract(totalRefunds);
    SalesReport report = new SalesReport();
    report.setFrom(from);
    report.setTo(to);
    report.setRows(rows);
    report.setTotalQuantity(totalQuantity);
    report.setTotalRevenue(totalRevenue);
    report.setTotalRefunds(totalRefunds);
    report.setTotalNet(totalNet);
    return report;
  }

  public String salesCsv(SalesReport report) {
    StringBuilder csv = new StringBuilder("book_id,book_title,quantity,revenue,refunds,net\n");
    for (SalesRow row : report.getRows()) {
      csv.append(row.getBookId()).append(',')
          .append('"').append(row.getBookTitle().replace("\"", "\"\"")).append('"').append(',')
          .append(row.getQuantity()).append(',')
          .append(row.getRevenue()).append(',')
          .append(row.getRefunds()).append(',')
          .append(row.getNet()).append('\n');
    }
    csv.append("TOTAL,,").append(report.getTotalQuantity()).append(',')
        .append(report.getTotalRevenue()).append(',')
        .append(report.getTotalRefunds()).append(',')
        .append(report.getTotalNet()).append('\n');
    return csv.toString();
  }

  public RoyaltyLiability royaltyLiability() {
    UUID orgId = TenantContext.require();
    BigDecimal draft = BigDecimal.ZERO;
    BigDecimal approved = BigDecimal.ZERO;
    BigDecimal paid = BigDecimal.ZERO;
    Map<UUID, BigDecimal> pendingByContract = new HashMap<>();
    for (RoyaltyStatement s : statements.findByOrganizationId(orgId, Pageable.unpaged())) {
      switch (s.getStatus()) {
        case DRAFT -> {
          draft = draft.add(s.getPayable());
          pendingByContract.merge(s.getContract().getId(), s.getPayable(), BigDecimal::add);
        }
        case APPROVED -> {
          approved = approved.add(s.getPayable());
          pendingByContract.merge(s.getContract().getId(), s.getPayable(), BigDecimal::add);
        }
        case PAID -> paid = paid.add(s.getPayable());
      }
    }
    Map<UUID, String> names = new HashMap<>();
    contracts.findByOrganizationId(orgId).forEach(c -> {
      if (c.getContributor() != null) {
        names.put(c.getId(), c.getContributor().getFullName());
      }
    });
    List<ContributorLiability> byContributor = pendingByContract.entrySet().stream()
        .map(e -> new ContributorLiability(e.getKey().toString(),
            names.getOrDefault(e.getKey(), "?"), e.getValue()))
        .sorted(Comparator.comparing(ContributorLiability::getContributorName))
        .toList();
    RoyaltyLiability liability = new RoyaltyLiability();
    liability.setDraftTotal(draft);
    liability.setApprovedTotal(approved);
    liability.setPaidTotal(paid);
    liability.setByContributor(byContributor);
    return liability;
  }

  public EditorialReport editorialReport(int days) {
    UUID orgId = TenantContext.require();
    LocalDate to = LocalDate.now(ZoneOffset.UTC);
    LocalDate from = to.minusDays(Math.max(days, 1) - 1);
    Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    int submitted = 0;
    int approved = 0;
    Map<ManuscriptStage, Integer> transitions = new HashMap<>();
    for (Manuscript m : manuscripts.findByOrganizationId(orgId, Pageable.unpaged())) {
      if (m.getCreatedAt() != null && !m.getCreatedAt().isBefore(start)) {
        submitted++;
      }
      for (ManuscriptEvent e : events.findByManuscriptIdAndCreatedAtBetween(m.getId(), start,
          end)) {
        transitions.merge(e.getToStage(), 1, Integer::sum);
        if (e.getToStage() == ManuscriptStage.APPROVED) {
          approved++;
        }
      }
    }
    List<StageCount> counts = transitions.entrySet().stream()
        .map(e -> new StageCount(e.getKey().name(), e.getValue()))
        .sorted(Comparator.comparing(StageCount::getStage))
        .toList();
    EditorialReport report = new EditorialReport();
    report.setFrom(from);
    report.setTo(to);
    report.setSubmitted(submitted);
    report.setApproved(approved);
    report.setTransitions(counts);
    return report;
  }

  private boolean inWindow(Instant at, Instant from, Instant to) {
    return at != null && !at.isBefore(from) && at.isBefore(to);
  }

  private static class SalesAccumulator {
    int quantity;
    BigDecimal revenue = BigDecimal.ZERO;
    BigDecimal refunds = BigDecimal.ZERO;
  }
}
