package com.smartline.smartos.modules.finance;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.finance.FinanceDtos.CreateTransactionRequest;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.sales.Order;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.OrderRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {

  private final TransactionRepository transactions;
  private final OrganizationRepository organizations;
  private final OrderRepository orders;

  public FinanceService(TransactionRepository transactions, OrganizationRepository organizations,
      OrderRepository orders) {
    this.transactions = transactions;
    this.organizations = organizations;
    this.orders = orders;
  }

  /**
   * Manual postings are limited to EXPENSE and REFUND. SALE and ROYALTY_PAYOUT
   * are system-posted (order fulfilment, statement payment) so revenue can
   * never be conjured by hand.
   */
  @Transactional
  @Audited(action = "finance.transaction.record", entityType = "Transaction")
  public Transaction recordManual(CreateTransactionRequest req) {
    UUID orgId = TenantContext.require();
    if (req.getType() != TransactionType.EXPENSE && req.getType() != TransactionType.REFUND) {
      throw ApiException.badRequest("SALE and ROYALTY_PAYOUT postings are system-generated");
    }
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    String referenceType = null;
    if (req.getType() == TransactionType.REFUND) {
      if (req.getReferenceId() == null) {
        throw ApiException.badRequest("Refunds must reference a fulfilled order");
      }
      Order order = orders.findByIdAndOrganizationId(req.getReferenceId(), orgId)
          .orElseThrow(() -> ApiException.notFound("Order", req.getReferenceId()));
      if (order.getStatus() != OrderStatus.FULFILLED) {
        throw ApiException.badRequest("Refunds must reference a fulfilled order");
      }
      referenceType = "ORDER";
    }
    Transaction transaction = new Transaction();
    transaction.setOrganization(org);
    transaction.setType(req.getType());
    transaction.setAmount(req.getAmount());
    transaction.setCategory(req.getCategory());
    transaction.setReferenceType(referenceType);
    transaction.setReferenceId(req.getReferenceId());
    transaction.setDescription(req.getDescription());
    return transactions.save(transaction);
  }

  @Transactional(readOnly = true)
  public Page<Transaction> list(TransactionType type, String category, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (type != null) {
      return transactions.findByOrganizationIdAndTypeOrderByCreatedAtDesc(orgId, type, pageable);
    }
    if (category != null && !category.isBlank()) {
      return transactions.findByOrganizationIdAndCategoryOrderByCreatedAtDesc(orgId,
          category.trim(), pageable);
    }
    return transactions.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable);
  }
}
