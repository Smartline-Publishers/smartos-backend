package com.smartline.smartos.modules.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.inventory.InventoryService;
import com.smartline.smartos.modules.inventory.StockMovement.MovementType;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.production.ProductionDtos.CreateJobRequest;
import com.smartline.smartos.modules.production.ProductionDtos.CreateRunRequest;
import com.smartline.smartos.modules.production.ProductionJob.JobStatus;
import com.smartline.smartos.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProductionServiceTest {

  @Mock private ProductionJobRepository jobs;
  @Mock private PrintRunRepository runs;
  @Mock private InventoryService inventory;
  @Mock private OrganizationRepository organizations;
  @Mock private BookRepository books;
  @Mock private ManuscriptRepository manuscripts;

  private ProductionService service;
  private UUID orgId;
  private Organization org;
  private Book book;

  @BeforeEach
  void setUp() {
    service = new ProductionService(jobs, runs, inventory, organizations, books, manuscripts);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    book = new Book();
    book.setId(UUID.randomUUID());
    book.setOrganization(org);
    book.setTitle("Maths 4");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void postRunCreatesStockMovementAtomically() {
    ProductionJob job = jobAt(JobStatus.IN_PROGRESS);
    PrintRun run = runOf(job, 500, false);
    UUID warehouseId = UUID.randomUUID();
    when(runs.findByIdAndJob_Organization_Id(eq(run.getId()), eq(orgId)))
        .thenReturn(Optional.of(run));
    when(runs.save(any(PrintRun.class))).thenAnswer(inv -> inv.getArgument(0));

    PrintRun posted = service.postRun(run.getId(), warehouseId);

    assertTrue(posted.isPosted());
    ArgumentCaptor<Integer> qty = ArgumentCaptor.forClass(Integer.class);
    verify(inventory).recordMovement(eq(book.getId()), eq(warehouseId), qty.capture(),
        eq(MovementType.IN), eq("PRINT_RUN"), eq(run.getId()), any());
    assertEquals(500, qty.getValue());
  }

  @Test
  void doublePostThrows409AndWritesNothing() {
    ProductionJob job = jobAt(JobStatus.IN_PROGRESS);
    PrintRun run = runOf(job, 500, true);
    when(runs.findByIdAndJob_Organization_Id(eq(run.getId()), eq(orgId)))
        .thenReturn(Optional.of(run));

    ApiException ex = assertThrows(ApiException.class,
        () -> service.postRun(run.getId(), UUID.randomUUID()));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    verify(runs, never()).save(any());
    verify(inventory, never()).recordMovement(any(), any(), any(int.class), any(), any(), any(),
        any());
  }

  @Test
  void postOnPlannedJobThrows400() {
    ProductionJob job = jobAt(JobStatus.PLANNED);
    PrintRun run = runOf(job, 100, false);
    when(runs.findByIdAndJob_Organization_Id(eq(run.getId()), eq(orgId)))
        .thenReturn(Optional.of(run));

    assertThrows(ApiException.class, () -> service.postRun(run.getId(), UUID.randomUUID()));
  }

  @Test
  void completeWithUnpostedRunsThrows400() {
    ProductionJob job = jobAt(JobStatus.IN_PROGRESS);
    when(jobs.findByIdAndOrganization_Id(eq(job.getId()), eq(orgId)))
        .thenReturn(Optional.of(job));
    when(runs.existsByJobIdAndPostedFalse(eq(job.getId()))).thenReturn(true);

    ApiException ex = assertThrows(ApiException.class, () -> service.completeJob(job.getId()));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  void cancelWithPostedRunsThrows409() {
    ProductionJob job = jobAt(JobStatus.IN_PROGRESS);
    when(jobs.findByIdAndOrganization_Id(eq(job.getId()), eq(orgId)))
        .thenReturn(Optional.of(job));
    when(runs.existsByJobIdAndPostedTrue(eq(job.getId()))).thenReturn(true);

    ApiException ex = assertThrows(ApiException.class, () -> service.cancelJob(job.getId()));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
  }

  @Test
  void createRunOnCompletedJobThrows400() {
    ProductionJob job = jobAt(JobStatus.COMPLETED);
    when(jobs.findByIdAndOrganization_Id(eq(job.getId()), eq(orgId)))
        .thenReturn(Optional.of(job));

    CreateRunRequest req = new CreateRunRequest();
    req.setQuantity(100);

    assertThrows(ApiException.class, () -> service.createRun(job.getId(), req));
  }

  @Test
  void createJobLinksScopedBook() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(books.findByIdAndOrganization_Id(eq(book.getId()), eq(orgId)))
        .thenReturn(Optional.of(book));
    when(jobs.save(any(ProductionJob.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateJobRequest req = new CreateJobRequest();
    req.setBookId(book.getId());
    req.setQuantity(5000);
    ProductionJob saved = service.createJob(req);

    assertEquals(JobStatus.PLANNED, saved.getStatus());
    assertEquals(book.getId(), saved.getBook().getId());
  }

  private ProductionJob jobAt(JobStatus status) {
    ProductionJob job = new ProductionJob();
    job.setId(UUID.randomUUID());
    job.setOrganization(org);
    job.setBook(book);
    job.setStatus(status);
    job.setQuantity(5000);
    return job;
  }

  private PrintRun runOf(ProductionJob job, int quantity, boolean posted) {
    PrintRun run = new PrintRun();
    run.setId(UUID.randomUUID());
    run.setJob(job);
    run.setQuantity(quantity);
    run.setPosted(posted);
    return run;
  }
}
