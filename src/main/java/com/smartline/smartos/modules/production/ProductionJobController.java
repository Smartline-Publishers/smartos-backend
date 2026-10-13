package com.smartline.smartos.modules.production;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.production.ProductionDtos.CreateJobRequest;
import com.smartline.smartos.modules.production.ProductionDtos.CreateRunRequest;
import com.smartline.smartos.modules.production.ProductionDtos.JobResponse;
import com.smartline.smartos.modules.production.ProductionDtos.PostRunRequest;
import com.smartline.smartos.modules.production.ProductionDtos.PrintRunResponse;
import com.smartline.smartos.modules.production.ProductionDtos.ProductionMapper;
import com.smartline.smartos.modules.production.ProductionJob.JobStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/jobs")
public class ProductionJobController {

  private final ProductionService service;

  public ProductionJobController(ProductionService service) {
    this.service = service;
  }

  private JobResponse withRuns(ProductionJob job) {
    List<PrintRunResponse> runs = service.jobRuns(job.getId()).stream()
        .map(ProductionMapper::toResponse)
        .toList();
    return ProductionMapper.toResponse(job, runs);
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<PageResponse<JobResponse>> list(
      @RequestParam(required = false) JobStatus status,
      @RequestParam(required = false) UUID bookId,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.listJobs(status, bookId, Paging.capped(pageable)).map(this::withRuns)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<JobResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(withRuns(service.getJob(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PRODUCTION')")
  public ApiResponse<JobResponse> create(@Valid @RequestBody CreateJobRequest req) {
    return ApiResponse.ok(withRuns(service.createJob(req)));
  }

  @PostMapping("/{id}/start")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PRODUCTION')")
  public ApiResponse<JobResponse> start(@PathVariable UUID id) {
    return ApiResponse.ok(withRuns(service.startJob(id)));
  }

  @PostMapping("/{id}/complete")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PRODUCTION')")
  public ApiResponse<JobResponse> complete(@PathVariable UUID id) {
    return ApiResponse.ok(withRuns(service.completeJob(id)));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PRODUCTION')")
  public ApiResponse<JobResponse> cancel(@PathVariable UUID id) {
    return ApiResponse.ok(withRuns(service.cancelJob(id)));
  }

  @PostMapping("/{id}/runs")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PRODUCTION')")
  public ApiResponse<PrintRunResponse> createRun(@PathVariable UUID id,
      @Valid @RequestBody CreateRunRequest req) {
    return ApiResponse.ok(ProductionMapper.toResponse(service.createRun(id, req)));
  }

  @PostMapping("/runs/{runId}/post")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PRODUCTION')")
  public ApiResponse<PrintRunResponse> postRun(@PathVariable UUID runId,
      @Valid @RequestBody PostRunRequest req) {
    return ApiResponse.ok(ProductionMapper.toResponse(service.postRun(runId, req.getWarehouseId())));
  }

  @DeleteMapping("/runs/{runId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PRODUCTION')")
  public void deleteRun(@PathVariable UUID runId) {
    service.deleteRun(runId);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PRODUCTION')")
  public void delete(@PathVariable UUID id) {
    service.deleteJob(id);
  }
}
