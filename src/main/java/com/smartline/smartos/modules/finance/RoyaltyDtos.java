package com.smartline.smartos.modules.finance;

import com.smartline.smartos.modules.finance.Contract.ContractStatus;
import com.smartline.smartos.modules.finance.RoyaltyStatement.StatementStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RoyaltyDtos {

  private RoyaltyDtos() {
  }

  public static class CreateContractRequest {
    @NotNull private UUID contributorId;
    private UUID bookId;
    @NotNull @DecimalMin(value = "0", message = "Rate must be between 0 and 1")
    @DecimalMax(value = "1", message = "Rate must be between 0 and 1")
    private BigDecimal royaltyRate;
    @NotNull private LocalDate startDate;
    private LocalDate endDate;

    public CreateContractRequest() {
    }

    public UUID getContributorId() {
      return contributorId;
    }

    public void setContributorId(UUID contributorId) {
      this.contributorId = contributorId;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public BigDecimal getRoyaltyRate() {
      return royaltyRate;
    }

    public void setRoyaltyRate(BigDecimal royaltyRate) {
      this.royaltyRate = royaltyRate;
    }

    public LocalDate getStartDate() {
      return startDate;
    }

    public void setStartDate(LocalDate startDate) {
      this.startDate = startDate;
    }

    public LocalDate getEndDate() {
      return endDate;
    }

    public void setEndDate(LocalDate endDate) {
      this.endDate = endDate;
    }
  }

  public static class ContractResponse {
    private UUID id;
    private UUID organizationId;
    private UUID contributorId;
    private String contributorName;
    private UUID bookId;
    private BigDecimal royaltyRate;
    private LocalDate startDate;
    private LocalDate endDate;
    private ContractStatus status;

    public ContractResponse() {
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

    public UUID getContributorId() {
      return contributorId;
    }

    public void setContributorId(UUID contributorId) {
      this.contributorId = contributorId;
    }

    public String getContributorName() {
      return contributorName;
    }

    public void setContributorName(String contributorName) {
      this.contributorName = contributorName;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public BigDecimal getRoyaltyRate() {
      return royaltyRate;
    }

    public void setRoyaltyRate(BigDecimal royaltyRate) {
      this.royaltyRate = royaltyRate;
    }

    public LocalDate getStartDate() {
      return startDate;
    }

    public void setStartDate(LocalDate startDate) {
      this.startDate = startDate;
    }

    public LocalDate getEndDate() {
      return endDate;
    }

    public void setEndDate(LocalDate endDate) {
      this.endDate = endDate;
    }

    public ContractStatus getStatus() {
      return status;
    }

    public void setStatus(ContractStatus status) {
      this.status = status;
    }
  }

  public static class GenerateStatementRequest {
    @NotNull private UUID contractId;
    @NotNull private LocalDate periodStart;
    @NotNull private LocalDate periodEnd;

    public GenerateStatementRequest() {
    }

    public UUID getContractId() {
      return contractId;
    }

    public void setContractId(UUID contractId) {
      this.contractId = contractId;
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
  }

  public static class RoyaltyStatementResponse {
    private UUID id;
    private UUID organizationId;
    private UUID contractId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal netSales;
    private BigDecimal rateSnapshot;
    private BigDecimal payable;
    private StatementStatus status;
    private UUID approvedBy;
    private Instant approvedAt;
    private Instant paidAt;

    public RoyaltyStatementResponse() {
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

    public UUID getContractId() {
      return contractId;
    }

    public void setContractId(UUID contractId) {
      this.contractId = contractId;
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

    public BigDecimal getNetSales() {
      return netSales;
    }

    public void setNetSales(BigDecimal netSales) {
      this.netSales = netSales;
    }

    public BigDecimal getRateSnapshot() {
      return rateSnapshot;
    }

    public void setRateSnapshot(BigDecimal rateSnapshot) {
      this.rateSnapshot = rateSnapshot;
    }

    public BigDecimal getPayable() {
      return payable;
    }

    public void setPayable(BigDecimal payable) {
      this.payable = payable;
    }

    public StatementStatus getStatus() {
      return status;
    }

    public void setStatus(StatementStatus status) {
      this.status = status;
    }

    public UUID getApprovedBy() {
      return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
      this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
      return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
      this.approvedAt = approvedAt;
    }

    public Instant getPaidAt() {
      return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
      this.paidAt = paidAt;
    }
  }

  public static class RoyaltyMapper {
    private RoyaltyMapper() {
    }

    public static ContractResponse toResponse(Contract c, String contributorName) {
      UUID orgId = c.getOrganization() != null ? c.getOrganization().getId()
          : c.getOrganizationId();
      ContractResponse r = new ContractResponse();
      r.setId(c.getId());
      r.setOrganizationId(orgId);
      r.setContributorId(c.getContributor() != null ? c.getContributor().getId() : null);
      r.setContributorName(contributorName);
      r.setBookId(c.getBook() != null ? c.getBook().getId() : null);
      r.setRoyaltyRate(c.getRoyaltyRate());
      r.setStartDate(c.getStartDate());
      r.setEndDate(c.getEndDate());
      r.setStatus(c.getStatus());
      return r;
    }

    public static RoyaltyStatementResponse toResponse(RoyaltyStatement s) {
      UUID orgId = s.getOrganization() != null ? s.getOrganization().getId()
          : s.getOrganizationId();
      RoyaltyStatementResponse r = new RoyaltyStatementResponse();
      r.setId(s.getId());
      r.setOrganizationId(orgId);
      r.setContractId(s.getContract() != null ? s.getContract().getId() : null);
      r.setPeriodStart(s.getPeriodStart());
      r.setPeriodEnd(s.getPeriodEnd());
      r.setNetSales(s.getNetSales());
      r.setRateSnapshot(s.getRateSnapshot());
      r.setPayable(s.getPayable());
      r.setStatus(s.getStatus());
      r.setApprovedBy(s.getApprovedBy());
      r.setApprovedAt(s.getApprovedAt());
      r.setPaidAt(s.getPaidAt());
      return r;
    }
  }
}
