package com.smartline.smartos.modules.finance;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.BookContributor;
import com.smartline.smartos.modules.book.BookContributorRepository;
import com.smartline.smartos.modules.finance.Contract.ContractStatus;
import com.smartline.smartos.modules.finance.RoyaltyStatement.StatementStatus;
import com.smartline.smartos.modules.finance.RoyaltyDtos.GenerateStatementRequest;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.sales.Order;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.OrderItem;
import com.smartline.smartos.modules.sales.OrderItemRepository;
import com.smartline.smartos.modules.sales.OrderRepository;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoyaltyService {

  private final RoyaltyStatementRepository statements;
  private final ContractRepository contracts;
  private final TransactionRepository transactions;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final BookContributorRepository bookContributors;

  public RoyaltyService(RoyaltyStatementRepository statements, ContractRepository contracts,
      TransactionRepository transactions, OrderRepository orders, OrderItemRepository orderItems,
      BookContributorRepository bookContributors) {
    this.statements = statements;
    this.contracts = contracts;
    this.transactions = transactions;
    this.orders = orders;
    this.orderItems = orderItems;
    this.bookContributors = bookContributors;
  }

  /**
   * Generates a DRAFT statement: net = fulfilled-order revenue minus refunds
   * in the period, payable = net × contract rate (applied per book through the
   * contributor's share). Snapshots net and rate so later sales never rewrite
   * this statement.
   */
  @Transactional
  @Audited(action = "royalty.statement.generate", entityType = "RoyaltyStatement")
  public RoyaltyStatement generate(GenerateStatementRequest req) {
    UUID orgId = TenantContext.require();
    if (req.getPeriodEnd().isBefore(req.getPeriodStart())) {
      throw ApiException.badRequest("Statement period end must not precede start");
    }
    Contract contract = contracts.findByIdAndOrganizationId(req.getContractId(), orgId)
        .orElseThrow(() -> ApiException.notFound("Contract", req.getContractId()));
    if (contract.getStatus() != ContractStatus.ACTIVE) {
      throw ApiException.badRequest("Statements can only be generated for active contracts");
    }
    UUID contributorId = contract.getContributor().getId();
    Set<UUID> bookIds = bookIdsFor(contract, contributorId);
    Instant from = req.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = req.getPeriodEnd().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    Map<UUID, BigDecimal> revenueByBook = new HashMap<>();
    Map<UUID, BigDecimal> orderTotals = new HashMap<>();
    for (Order order : orders.findByOrganizationIdAndStatus(orgId, OrderStatus.FULFILLED)) {
      if (!inWindow(order.getUpdatedAt(), from, to)) {
        continue;
      }
      List<OrderItem> lines = orderItems.findByOrderId(order.getId());
      BigDecimal orderTotal = BigDecimal.ZERO;
      Map<UUID, BigDecimal> linesByBook = new HashMap<>();
      for (OrderItem item : lines) {
        UUID bookId = item.getBook().getId();
        BigDecimal line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        orderTotal = orderTotal.add(line);
        if (bookIds.contains(bookId)) {
          linesByBook.merge(bookId, line, BigDecimal::add);
        }
      }
      orderTotals.put(order.getId(), orderTotal);
      linesByBook.forEach((bookId, line) ->
          revenueByBook.merge(bookId, line, BigDecimal::add));
    }
    Map<UUID, BigDecimal> refundsByBook = new HashMap<>();
    for (Transaction refund : transactions.findByOrganizationIdAndTypeAndCreatedAtBetween(orgId,
        TransactionType.REFUND, from, to)) {
      if (!"ORDER".equals(refund.getReferenceType()) || refund.getReferenceId() == null) {
        continue;
      }
      List<OrderItem> lines = orderItems.findByOrderId(refund.getReferenceId());
      BigDecimal orderTotal = orderTotals.getOrDefault(refund.getReferenceId(), BigDecimal.ZERO);
      if (orderTotal.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      for (OrderItem item : lines) {
        UUID bookId = item.getBook().getId();
        if (!bookIds.contains(bookId)) {
          continue;
        }
        BigDecimal line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        BigDecimal share = line.divide(orderTotal, 10, RoundingMode.HALF_UP);
        refundsByBook.merge(bookId, refund.getAmount().multiply(share), BigDecimal::add);
      }
    }
    Map<UUID, BigDecimal> shares = shareByBook(contributorId, bookIds);
    BigDecimal net = BigDecimal.ZERO;
    for (UUID bookId : bookIds) {
      BigDecimal revenue = revenueByBook.getOrDefault(bookId, BigDecimal.ZERO);
      BigDecimal refunds = refundsByBook.getOrDefault(bookId, BigDecimal.ZERO);
      BigDecimal share = shares.getOrDefault(bookId, BigDecimal.ONE);
      net = net.add(revenue.subtract(refunds).multiply(share));
    }
    net = net.setScale(2, RoundingMode.HALF_UP);
    BigDecimal payable = net.multiply(contract.getRoyaltyRate()).setScale(2, RoundingMode.HALF_UP);

    Organization org = contract.getOrganization();
    RoyaltyStatement statement = new RoyaltyStatement();
    statement.setOrganization(org);
    statement.setContract(contract);
    statement.setPeriodStart(req.getPeriodStart());
    statement.setPeriodEnd(req.getPeriodEnd());
    statement.setNetSales(net);
    statement.setRateSnapshot(contract.getRoyaltyRate());
    statement.setPayable(payable);
    statement.setStatus(StatementStatus.DRAFT);
    return statements.save(statement);
  }

  @Transactional(readOnly = true)
  public Page<RoyaltyStatement> list(StatementStatus status, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (status != null) {
      return statements.findByOrganizationIdAndStatus(orgId, status, pageable);
    }
    return statements.findByOrganizationId(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public RoyaltyStatement get(UUID id) {
    return statements.findByIdAndOrganizationId(id, TenantContext.require())
        .orElseThrow(() -> ApiException.notFound("RoyaltyStatement", id));
  }

  @Transactional
  @Audited(action = "royalty.statement.approve", entityType = "RoyaltyStatement")
  public RoyaltyStatement approve(UUID id) {
    RoyaltyStatement statement = get(id);
    if (statement.getStatus() != StatementStatus.DRAFT) {
      throw ApiException.badRequest("Only draft statements can be approved");
    }
    statement.setStatus(StatementStatus.APPROVED);
    statement.setApprovedBy(currentActorId());
    statement.setApprovedAt(Instant.now());
    return statements.save(statement);
  }

  /**
   * Pays an approved statement and posts the matching ROYALTY_PAYOUT
   * transaction in the same database transaction.
   */
  @Transactional
  @Audited(action = "royalty.statement.pay", entityType = "RoyaltyStatement")
  public RoyaltyStatement pay(UUID id) {
    RoyaltyStatement statement = get(id);
    if (statement.getStatus() != StatementStatus.APPROVED) {
      throw ApiException.badRequest("Only approved statements can be paid");
    }
    Transaction payout = new Transaction();
    payout.setOrganization(statement.getOrganization());
    payout.setType(TransactionType.ROYALTY_PAYOUT);
    payout.setAmount(statement.getPayable());
    payout.setCategory("ROYALTIES");
    payout.setReferenceType("STATEMENT");
    payout.setReferenceId(statement.getId());
    payout.setDescription("Royalty payout");
    transactions.save(payout);
    statement.setStatus(StatementStatus.PAID);
    statement.setPaidAt(Instant.now());
    return statements.save(statement);
  }

  private Set<UUID> bookIdsFor(Contract contract, UUID contributorId) {
    if (contract.getBook() != null) {
      return Set.of(contract.getBook().getId());
    }
    Set<UUID> ids = new HashSet<>();
    for (BookContributor link : bookContributors.findByContributorId(contributorId)) {
      if (link.getBook() != null) {
        ids.add(link.getBook().getId());
      }
    }
    return ids;
  }

  private Map<UUID, BigDecimal> shareByBook(UUID contributorId, Set<UUID> bookIds) {
    Map<UUID, BigDecimal> shares = new HashMap<>();
    for (BookContributor link : bookContributors.findByContributorId(contributorId)) {
      if (link.getBook() != null && bookIds.contains(link.getBook().getId())) {
        Double pct = link.getRoyaltyPct();
        shares.put(link.getBook().getId(),
            pct != null ? BigDecimal.valueOf(pct).divide(BigDecimal.valueOf(100)) : BigDecimal.ONE);
      }
    }
    return shares;
  }

  private boolean inWindow(Instant at, Instant from, Instant to) {
    return at != null && !at.isBefore(from) && at.isBefore(to);
  }

  private UUID currentActorId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      return principal.getUserId();
    }
    return null;
  }
}
