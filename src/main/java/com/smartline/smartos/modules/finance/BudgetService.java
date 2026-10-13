package com.smartline.smartos.modules.finance;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.finance.FinanceDtos.BudgetResponse;
import com.smartline.smartos.modules.finance.FinanceDtos.CreateBudgetRequest;
import com.smartline.smartos.modules.finance.FinanceDtos.UpdateBudgetRequest;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

  private final BudgetRepository budgets;
  private final TransactionRepository transactions;
  private final OrganizationRepository organizations;

  public BudgetService(BudgetRepository budgets, TransactionRepository transactions,
      OrganizationRepository organizations) {
    this.budgets = budgets;
    this.transactions = transactions;
    this.organizations = organizations;
  }

  @Transactional
  @Audited(action = "finance.budget.create", entityType = "Budget")
  public Budget create(CreateBudgetRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    checkPeriod(req.getPeriodStart(), req.getPeriodEnd());
    Budget budget = new Budget();
    budget.setOrganization(org);
    budget.setName(req.getName().trim());
    budget.setCategory(req.getCategory());
    budget.setPeriodStart(req.getPeriodStart());
    budget.setPeriodEnd(req.getPeriodEnd());
    budget.setPlannedAmount(req.getPlannedAmount());
    return budgets.save(budget);
  }

  @Transactional(readOnly = true)
  public Page<Budget> list(Pageable pageable) {
    return budgets.findByOrganizationId(TenantContext.require(), pageable);
  }

  @Transactional(readOnly = true)
  public Budget get(UUID id) {
    return budgets.findByIdAndOrganizationId(id, TenantContext.require())
        .orElseThrow(() -> ApiException.notFound("Budget", id));
  }

  @Transactional(readOnly = true)
  public BudgetResponse withActuals(UUID id) {
    Budget budget = get(id);
    BigDecimal actual = actualSpending(TenantContext.require(), budget);
    BudgetResponse r = new BudgetResponse();
    r.setId(budget.getId());
    r.setOrganizationId(budget.getOrganizationId());
    r.setName(budget.getName());
    r.setCategory(budget.getCategory());
    r.setPeriodStart(budget.getPeriodStart());
    r.setPeriodEnd(budget.getPeriodEnd());
    r.setPlannedAmount(budget.getPlannedAmount());
    r.setActualAmount(actual);
    r.setVariance(budget.getPlannedAmount().subtract(actual));
    return r;
  }

  @Transactional
  @Audited(action = "finance.budget.update", entityType = "Budget")
  public Budget update(UUID id, UpdateBudgetRequest req) {
    Budget budget = get(id);
    if (req.getName() != null && !req.getName().isBlank()) {
      budget.setName(req.getName().trim());
    }
    if (req.getCategory() != null) {
      budget.setCategory(req.getCategory());
    }
    if (req.getPeriodStart() != null) {
      budget.setPeriodStart(req.getPeriodStart());
    }
    if (req.getPeriodEnd() != null) {
      budget.setPeriodEnd(req.getPeriodEnd());
    }
    checkPeriod(budget.getPeriodStart(), budget.getPeriodEnd());
    if (req.getPlannedAmount() != null) {
      budget.setPlannedAmount(req.getPlannedAmount());
    }
    return budgets.save(budget);
  }

  @Transactional
  @Audited(action = "finance.budget.delete", entityType = "Budget")
  public void delete(UUID id) {
    budgets.delete(get(id));
  }

  private void checkPeriod(LocalDate start, LocalDate end) {
    if (start != null && end != null && end.isBefore(start)) {
      throw ApiException.badRequest("Budget period end must not precede start");
    }
  }

  private BigDecimal actualSpending(UUID orgId, Budget budget) {
    Instant from = budget.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = budget.getPeriodEnd().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    BigDecimal actual = BigDecimal.ZERO;
    for (Transaction t : transactions.findByOrganizationIdAndTypeAndCreatedAtBetween(orgId,
        TransactionType.EXPENSE, from, to)) {
      if (budget.getCategory() == null || budget.getCategory().equals(t.getCategory())) {
        actual = actual.add(t.getAmount());
      }
    }
    return actual;
  }
}
