package com.smartline.smartos.modules.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportingDtos {

  private ReportingDtos() {
  }

  public static class SalesRow {
    private String bookId;
    private String bookTitle;
    private int quantity;
    private BigDecimal revenue;
    private BigDecimal refunds;
    private BigDecimal net;

    public SalesRow() {
    }

    public SalesRow(String bookId, String bookTitle, int quantity, BigDecimal revenue,
        BigDecimal refunds, BigDecimal net) {
      this.bookId = bookId;
      this.bookTitle = bookTitle;
      this.quantity = quantity;
      this.revenue = revenue;
      this.refunds = refunds;
      this.net = net;
    }

    public String getBookId() {
      return bookId;
    }

    public void setBookId(String bookId) {
      this.bookId = bookId;
    }

    public String getBookTitle() {
      return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
      this.bookTitle = bookTitle;
    }

    public int getQuantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    public BigDecimal getRevenue() {
      return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
      this.revenue = revenue;
    }

    public BigDecimal getRefunds() {
      return refunds;
    }

    public void setRefunds(BigDecimal refunds) {
      this.refunds = refunds;
    }

    public BigDecimal getNet() {
      return net;
    }

    public void setNet(BigDecimal net) {
      this.net = net;
    }
  }

  public static class SalesReport {
    private LocalDate from;
    private LocalDate to;
    private List<SalesRow> rows;
    private int totalQuantity;
    private BigDecimal totalRevenue;
    private BigDecimal totalRefunds;
    private BigDecimal totalNet;

    public SalesReport() {
    }

    public LocalDate getFrom() {
      return from;
    }

    public void setFrom(LocalDate from) {
      this.from = from;
    }

    public LocalDate getTo() {
      return to;
    }

    public void setTo(LocalDate to) {
      this.to = to;
    }

    public List<SalesRow> getRows() {
      return rows;
    }

    public void setRows(List<SalesRow> rows) {
      this.rows = rows;
    }

    public int getTotalQuantity() {
      return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
      this.totalQuantity = totalQuantity;
    }

    public BigDecimal getTotalRevenue() {
      return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
      this.totalRevenue = totalRevenue;
    }

    public BigDecimal getTotalRefunds() {
      return totalRefunds;
    }

    public void setTotalRefunds(BigDecimal totalRefunds) {
      this.totalRefunds = totalRefunds;
    }

    public BigDecimal getTotalNet() {
      return totalNet;
    }

    public void setTotalNet(BigDecimal totalNet) {
      this.totalNet = totalNet;
    }
  }

  public static class ContributorLiability {
    private String contributorId;
    private String contributorName;
    private BigDecimal pending;

    public ContributorLiability() {
    }

    public ContributorLiability(String contributorId, String contributorName, BigDecimal pending) {
      this.contributorId = contributorId;
      this.contributorName = contributorName;
      this.pending = pending;
    }

    public String getContributorId() {
      return contributorId;
    }

    public void setContributorId(String contributorId) {
      this.contributorId = contributorId;
    }

    public String getContributorName() {
      return contributorName;
    }

    public void setContributorName(String contributorName) {
      this.contributorName = contributorName;
    }

    public BigDecimal getPending() {
      return pending;
    }

    public void setPending(BigDecimal pending) {
      this.pending = pending;
    }
  }

  public static class RoyaltyLiability {
    private BigDecimal draftTotal;
    private BigDecimal approvedTotal;
    private BigDecimal paidTotal;
    private List<ContributorLiability> byContributor;

    public RoyaltyLiability() {
    }

    public BigDecimal getDraftTotal() {
      return draftTotal;
    }

    public void setDraftTotal(BigDecimal draftTotal) {
      this.draftTotal = draftTotal;
    }

    public BigDecimal getApprovedTotal() {
      return approvedTotal;
    }

    public void setApprovedTotal(BigDecimal approvedTotal) {
      this.approvedTotal = approvedTotal;
    }

    public BigDecimal getPaidTotal() {
      return paidTotal;
    }

    public void setPaidTotal(BigDecimal paidTotal) {
      this.paidTotal = paidTotal;
    }

    public List<ContributorLiability> getByContributor() {
      return byContributor;
    }

    public void setByContributor(List<ContributorLiability> byContributor) {
      this.byContributor = byContributor;
    }
  }

  public static class StageCount {
    private String stage;
    private int count;

    public StageCount() {
    }

    public StageCount(String stage, int count) {
      this.stage = stage;
      this.count = count;
    }

    public String getStage() {
      return stage;
    }

    public void setStage(String stage) {
      this.stage = stage;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }
  }

  public static class EditorialReport {
    private LocalDate from;
    private LocalDate to;
    private int submitted;
    private int approved;
    private List<StageCount> transitions;

    public EditorialReport() {
    }

    public LocalDate getFrom() {
      return from;
    }

    public void setFrom(LocalDate from) {
      this.from = from;
    }

    public LocalDate getTo() {
      return to;
    }

    public void setTo(LocalDate to) {
      this.to = to;
    }

    public int getSubmitted() {
      return submitted;
    }

    public void setSubmitted(int submitted) {
      this.submitted = submitted;
    }

    public int getApproved() {
      return approved;
    }

    public void setApproved(int approved) {
      this.approved = approved;
    }

    public List<StageCount> getTransitions() {
      return transitions;
    }

    public void setTransitions(List<StageCount> transitions) {
      this.transitions = transitions;
    }
  }
}
