package com.smartline.smartos.modules.production;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.inventory.InventoryService;
import com.smartline.smartos.modules.inventory.StockMovement.MovementType;
import com.smartline.smartos.modules.manuscript.Manuscript;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.production.ProductionDtos.CreateJobRequest;
import com.smartline.smartos.modules.production.ProductionDtos.CreateRunRequest;
import com.smartline.smartos.modules.production.ProductionJob.JobStatus;
import com.smartline.smartos.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionService {

  private final ProductionJobRepository jobs;
  private final PrintRunRepository runs;
  private final InventoryService inventory;
  private final OrganizationRepository organizations;
  private final BookRepository books;
  private final ManuscriptRepository manuscripts;

  public ProductionService(ProductionJobRepository jobs, PrintRunRepository runs,
      InventoryService inventory, OrganizationRepository organizations, BookRepository books,
      ManuscriptRepository manuscripts) {
    this.jobs = jobs;
    this.runs = runs;
    this.inventory = inventory;
    this.organizations = organizations;
    this.books = books;
    this.manuscripts = manuscripts;
  }

  @Transactional
  @Audited(action = "production.job.create", entityType = "ProductionJob")
  public ProductionJob createJob(CreateJobRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    Book book = books.findByIdAndOrganization_Id(req.getBookId(), orgId)
        .orElseThrow(() -> ApiException.notFound("Book", req.getBookId()));
    Manuscript manuscript = null;
    if (req.getManuscriptId() != null) {
      manuscript = manuscripts.findByIdAndOrganization_Id(req.getManuscriptId(), orgId)
          .orElseThrow(() -> ApiException.notFound("Manuscript", req.getManuscriptId()));
    }
    ProductionJob job = new ProductionJob();
    job.setOrganization(org);
    job.setBook(book);
    job.setManuscript(manuscript);
    job.setQuantity(req.getQuantity());
    job.setStatus(JobStatus.PLANNED);
    job.setPrinter(req.getPrinter());
    job.setDueDate(req.getDueDate());
    return jobs.save(job);
  }

  @Transactional(readOnly = true)
  public Page<ProductionJob> listJobs(JobStatus status, UUID bookId, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (status != null) {
      return jobs.findByOrganization_IdAndStatus(orgId, status, pageable);
    }
    if (bookId != null) {
      return jobs.findByOrganization_IdAndBookId(orgId, bookId, pageable);
    }
    return jobs.findByOrganization_Id(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public ProductionJob getJob(UUID id) {
    return jobs.findByIdAndOrganization_Id(id, TenantContext.require())
        .orElseThrow(() -> ApiException.notFound("ProductionJob", id));
  }

  @Transactional(readOnly = true)
  public List<PrintRun> jobRuns(UUID jobId) {
    ProductionJob job = getJob(jobId);
    return runs.findByJobId(job.getId());
  }

  @Transactional
  @Audited(action = "production.job.start", entityType = "ProductionJob")
  public ProductionJob startJob(UUID id) {
    ProductionJob job = getJob(id);
    if (job.getStatus() != JobStatus.PLANNED) {
      throw ApiException.badRequest("Only planned jobs can be started");
    }
    job.setStatus(JobStatus.IN_PROGRESS);
    return jobs.save(job);
  }

  @Transactional
  @Audited(action = "production.job.complete", entityType = "ProductionJob")
  public ProductionJob completeJob(UUID id) {
    ProductionJob job = getJob(id);
    if (job.getStatus() != JobStatus.IN_PROGRESS) {
      throw ApiException.badRequest("Only in-progress jobs can be completed");
    }
    if (runs.existsByJobIdAndPostedFalse(job.getId())) {
      throw ApiException.badRequest("Post all print runs before completing the job");
    }
    job.setStatus(JobStatus.COMPLETED);
    return jobs.save(job);
  }

  @Transactional
  @Audited(action = "production.job.cancel", entityType = "ProductionJob")
  public ProductionJob cancelJob(UUID id) {
    ProductionJob job = getJob(id);
    if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.CANCELLED) {
      throw ApiException.badRequest("Completed or cancelled jobs cannot be cancelled");
    }
    if (runs.existsByJobIdAndPostedTrue(job.getId())) {
      throw ApiException.conflict("Job has posted stock and cannot be cancelled");
    }
    job.setStatus(JobStatus.CANCELLED);
    return jobs.save(job);
  }

  @Transactional
  @Audited(action = "production.job.delete", entityType = "ProductionJob")
  public void deleteJob(UUID id) {
    ProductionJob job = getJob(id);
    if (runs.existsByJobIdAndPostedTrue(job.getId())) {
      throw ApiException.conflict("Job has posted stock and cannot be deleted");
    }
    jobs.delete(job);
  }

  @Transactional
  @Audited(action = "production.run.create", entityType = "PrintRun")
  public PrintRun createRun(UUID jobId, CreateRunRequest req) {
    ProductionJob job = getJob(jobId);
    if (job.getStatus() != JobStatus.PLANNED && job.getStatus() != JobStatus.IN_PROGRESS) {
      throw ApiException.badRequest("Runs can only be added to planned or in-progress jobs");
    }
    PrintRun run = new PrintRun();
    run.setJob(job);
    run.setQuantity(req.getQuantity());
    run.setPosted(false);
    return runs.save(run);
  }

  @Transactional
  @Audited(action = "production.run.delete", entityType = "PrintRun")
  public void deleteRun(UUID runId) {
    PrintRun run = scopedRun(runId);
    if (run.isPosted()) {
      throw ApiException.conflict("Posted runs are immutable and cannot be deleted");
    }
    if (run.getJob().getStatus() == JobStatus.COMPLETED
        || run.getJob().getStatus() == JobStatus.CANCELLED) {
      throw ApiException.badRequest("Runs of completed or cancelled jobs are frozen");
    }
    runs.delete(run);
  }

  /**
   * Posts a print run: marks it posted and records the finished-books stock
   * movement in the same transaction, so stock can never drift from posting.
   */
  @Transactional
  @Audited(action = "production.run.post", entityType = "PrintRun")
  public PrintRun postRun(UUID runId, UUID warehouseId) {
    PrintRun run = scopedRun(runId);
    if (run.isPosted()) {
      throw ApiException.conflict("Print run already posted");
    }
    ProductionJob job = run.getJob();
    if (job.getStatus() != JobStatus.IN_PROGRESS) {
      throw ApiException.badRequest("Print runs can only be posted while the job is in progress");
    }
    run.setPosted(true);
    run.setPostedAt(Instant.now());
    PrintRun saved = runs.save(run);
    inventory.recordMovement(job.getBook().getId(), warehouseId, run.getQuantity(),
        MovementType.IN, "PRINT_RUN", run.getId(), "Print run posted");
    return saved;
  }

  private PrintRun scopedRun(UUID runId) {
    UUID orgId = TenantContext.require();
    return runs.findByIdAndJob_Organization_Id(runId, orgId)
        .orElseThrow(() -> ApiException.notFound("PrintRun", runId));
  }
}
