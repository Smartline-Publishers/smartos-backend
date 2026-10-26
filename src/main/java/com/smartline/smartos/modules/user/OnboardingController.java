package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.modules.user.OnboardingDtos.ChecklistResponse;
import com.smartline.smartos.modules.user.OnboardingDtos.UpdateOnboardingRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

  private final OnboardingService service;

  public OnboardingController(OnboardingService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<ChecklistResponse> checklist() {
    return ApiResponse.ok(service.checklist());
  }

  @PatchMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<ChecklistResponse> update(@RequestBody UpdateOnboardingRequest req) {
    return ApiResponse.ok(service.update(req));
  }
}
