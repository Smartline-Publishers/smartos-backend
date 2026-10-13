package com.smartline.smartos.modules.dashboard;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.modules.dashboard.DashboardDtos.DashboardSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<DashboardSummary> summary() {
    return ApiResponse.ok(service.summary());
  }
}
