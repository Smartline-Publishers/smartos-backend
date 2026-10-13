package com.smartline.smartos.modules.production;

import com.smartline.smartos.modules.production.ProductionJob.JobStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProductionDtos {

  private ProductionDtos() {
  }

  public static class CreateJobRequest {
    @NotNull private UUID bookId;
    private UUID manuscriptId;
    @NotNull @Min(1) private Integer quantity;
    @Size(max = 255) private String printer;
    private LocalDate dueDate;

    public CreateJobRequest() {
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getManuscriptId() {
      return manuscriptId;
    }

    public void setManuscriptId(UUID manuscriptId) {
      this.manuscriptId = manuscriptId;
    }

    public Integer getQuantity() {
      return quantity;
    }

    public void setQuantity(Integer quantity) {
      this.quantity = quantity;
    }

    public String getPrinter() {
      return printer;
    }

    public void setPrinter(String printer) {
      this.printer = printer;
    }

    public LocalDate getDueDate() {
      return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
    }
  }

  public static class CreateRunRequest {
    @NotNull @Min(1) private Integer quantity;

    public CreateRunRequest() {
    }

    public Integer getQuantity() {
      return quantity;
    }

    public void setQuantity(Integer quantity) {
      this.quantity = quantity;
    }
  }

  public static class PostRunRequest {
    @NotNull private UUID warehouseId;

    public PostRunRequest() {
    }

    public UUID getWarehouseId() {
      return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
      this.warehouseId = warehouseId;
    }
  }

  public static class PrintRunResponse {
    private UUID id;
    private UUID jobId;
    private int quantity;
    private boolean posted;
    private Instant postedAt;

    public PrintRunResponse() {
    }

    public PrintRunResponse(UUID id, UUID jobId, int quantity, boolean posted, Instant postedAt) {
      this.id = id;
      this.jobId = jobId;
      this.quantity = quantity;
      this.posted = posted;
      this.postedAt = postedAt;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getJobId() {
      return jobId;
    }

    public void setJobId(UUID jobId) {
      this.jobId = jobId;
    }

    public int getQuantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    public boolean isPosted() {
      return posted;
    }

    public void setPosted(boolean posted) {
      this.posted = posted;
    }

    public Instant getPostedAt() {
      return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
      this.postedAt = postedAt;
    }
  }

  public static class JobResponse {
    private UUID id;
    private UUID organizationId;
    private UUID bookId;
    private String bookTitle;
    private UUID manuscriptId;
    private JobStatus status;
    private int quantity;
    private String printer;
    private LocalDate dueDate;
    private Instant createdAt;
    private Instant updatedAt;
    private List<PrintRunResponse> runs;

    public JobResponse() {
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

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public String getBookTitle() {
      return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
      this.bookTitle = bookTitle;
    }

    public UUID getManuscriptId() {
      return manuscriptId;
    }

    public void setManuscriptId(UUID manuscriptId) {
      this.manuscriptId = manuscriptId;
    }

    public JobStatus getStatus() {
      return status;
    }

    public void setStatus(JobStatus status) {
      this.status = status;
    }

    public int getQuantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    public String getPrinter() {
      return printer;
    }

    public void setPrinter(String printer) {
      this.printer = printer;
    }

    public LocalDate getDueDate() {
      return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
    }

    public List<PrintRunResponse> getRuns() {
      return runs;
    }

    public void setRuns(List<PrintRunResponse> runs) {
      this.runs = runs;
    }
  }

  public static class ProductionMapper {
    private ProductionMapper() {
    }

    public static PrintRunResponse toResponse(PrintRun run) {
      UUID jobId = run.getJob() != null ? run.getJob().getId() : null;
      return new PrintRunResponse(run.getId(), jobId, run.getQuantity(), run.isPosted(),
          run.getPostedAt());
    }

    public static JobResponse toResponse(ProductionJob job, List<PrintRunResponse> runs) {
      UUID orgId = job.getOrganization() != null ? job.getOrganization().getId()
          : job.getOrganizationId();
      JobResponse r = new JobResponse();
      r.setId(job.getId());
      r.setOrganizationId(orgId);
      r.setBookId(job.getBook() != null ? job.getBook().getId() : null);
      r.setBookTitle(job.getBook() != null ? job.getBook().getTitle() : null);
      r.setManuscriptId(job.getManuscript() != null ? job.getManuscript().getId() : null);
      r.setStatus(job.getStatus());
      r.setQuantity(job.getQuantity());
      r.setPrinter(job.getPrinter());
      r.setDueDate(job.getDueDate());
      r.setCreatedAt(job.getCreatedAt());
      r.setUpdatedAt(job.getUpdatedAt());
      r.setRuns(runs != null ? runs : List.of());
      return r;
    }
  }
}
