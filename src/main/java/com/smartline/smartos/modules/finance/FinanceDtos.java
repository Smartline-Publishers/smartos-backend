package com.smartline.smartos.modules.finance;

import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FinanceDtos {

  private FinanceDtos() {
  }

  public static class CreateTransactionRequest {
    @NotNull private TransactionType type;
    @NotNull @DecimalMin(value = "0", inclusive = false, message = "Amount must be positive")
    private BigDecimal amount;
    @Size(max = 120) private String category;
    private UUID referenceId;
    @Size(max = 500) private String description;

    public CreateTransactionRequest() {
    }

    public TransactionType getType() {
      return type;
    }

    public void setType(TransactionType type) {
      this.type = type;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public UUID getReferenceId() {
      return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
      this.referenceId = referenceId;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }
  }

  public static class TransactionResponse {
    private UUID id;
    private UUID organizationId;
    private TransactionType type;
    private BigDecimal amount;
    private String category;
    private String referenceType;
    private UUID referenceId;
    private String description;
    private Instant createdAt;

    public TransactionResponse() {
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public TransactionType getType() {
      return type;
    }

    public void setType(TransactionType type) {
      this.type = type;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public String getReferenceType() {
      return referenceType;
    }

    public void setReferenceType(String referenceType) {
      this.referenceType = referenceType;
    }

    public UUID getReferenceId() {
      return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
      this.referenceId = referenceId;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }
  }

  public static class CreateBudgetRequest {
    @NotBlank private String name;
    @Size(max = 120) private String category;
    @NotNull private LocalDate periodStart;
    @NotNull private LocalDate periodEnd;
    @NotNull @DecimalMin(value = "0", message = "Planned amount must not be negative")
    private BigDecimal plannedAmount;

    public CreateBudgetRequest() {
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public LocalDate getPeriodStart() {
      return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
      this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
      return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
      this.periodEnd = periodEnd;
    }

    public BigDecimal getPlannedAmount() {
      return plannedAmount;
    }

    public void setPlannedAmount(BigDecimal plannedAmount) {
      this.plannedAmount = plannedAmount;
    }
  }

  public static class UpdateBudgetRequest {
    private String name;
    @Size(max = 120) private String category;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    @DecimalMin(value = "0", message = "Planned amount must not be negative")
    private BigDecimal plannedAmount;

    public UpdateBudgetRequest() {
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public LocalDate getPeriodStart() {
      return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
      this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
      return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
      this.periodEnd = periodEnd;
    }

    public BigDecimal getPlannedAmount() {
      return plannedAmount;
    }

    public void setPlannedAmount(BigDecimal plannedAmount) {
      this.plannedAmount = plannedAmount;
    }
  }

  public static class BudgetResponse {
    private UUID id;
    private UUID organizationId;
    private String name;
    private String category;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal plannedAmount;
    private BigDecimal actualAmount;
    private BigDecimal variance;

    public BudgetResponse() {
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public LocalDate getPeriodStart() {
      return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
      this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
      return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
      this.periodEnd = periodEnd;
    }

    public BigDecimal getPlannedAmount() {
      return plannedAmount;
    }

    public void setPlannedAmount(BigDecimal plannedAmount) {
      this.plannedAmount = plannedAmount;
    }

    public BigDecimal getActualAmount() {
      return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
      this.actualAmount = actualAmount;
    }

    public BigDecimal getVariance() {
      return variance;
    }

    public void setVariance(BigDecimal variance) {
      this.variance = variance;
    }
  }

  public static class FinanceMapper {
    private FinanceMapper() {
    }

    public static TransactionResponse toResponse(Transaction t) {
      UUID orgId = t.getOrganization() != null ? t.getOrganization().getId()
          : t.getOrganizationId();
      TransactionResponse r = new TransactionResponse();
      r.setId(t.getId());
      r.setOrganizationId(orgId);
      r.setType(t.getType());
      r.setAmount(t.getAmount());
      r.setCategory(t.getCategory());
      r.setReferenceType(t.getReferenceType());
      r.setReferenceId(t.getReferenceId());
      r.setDescription(t.getDescription());
      r.setCreatedAt(t.getCreatedAt());
      return r;
    }

    public static BudgetResponse toResponse(Budget b, BigDecimal actual) {
      UUID orgId = b.getOrganization() != null ? b.getOrganization().getId()
          : b.getOrganizationId();
      BudgetResponse r = new BudgetResponse();
      r.setId(b.getId());
      r.setOrganizationId(orgId);
      r.setName(b.getName());
      r.setCategory(b.getCategory());
      r.setPeriodStart(b.getPeriodStart());
      r.setPeriodEnd(b.getPeriodEnd());
      r.setPlannedAmount(b.getPlannedAmount());
      r.setActualAmount(actual);
      r.setVariance(b.getPlannedAmount().subtract(actual));
      return r;
    }
  }
}
