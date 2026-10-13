package com.smartline.smartos.modules.reporting;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.modules.reporting.ReportingDtos.EditorialReport;
import com.smartline.smartos.modules.reporting.ReportingDtos.RoyaltyLiability;
import com.smartline.smartos.modules.reporting.ReportingDtos.SalesReport;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

  private final ReportingService service;

  public ReportingController(ReportingService service) {
    this.service = service;
  }

  @GetMapping("/sales")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<SalesReport> sales(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID bookId) {
    LocalDate end = to != null ? to : LocalDate.now();
    LocalDate start = from != null ? from : end.minusDays(29);
    return ApiResponse.ok(service.salesReport(start, end, bookId));
  }

  @GetMapping(value = "/sales.csv", produces = "text/csv")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<String> salesCsv(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID bookId) {
    LocalDate end = to != null ? to : LocalDate.now();
    LocalDate start = from != null ? from : end.minusDays(29);
    String csv = service.salesCsv(service.salesReport(start, end, bookId));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("sales.csv").build().toString())
        .contentType(new MediaType("text", "csv"))
        .body(csv);
  }

  @GetMapping("/royalties")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<RoyaltyLiability> royalties() {
    return ApiResponse.ok(service.royaltyLiability());
  }

  @GetMapping("/editorial")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<EditorialReport> editorial(
      @RequestParam(required = false, defaultValue = "30") int days) {
    return ApiResponse.ok(service.editorialReport(Math.max(Math.min(days, 365), 1)));
  }
}
