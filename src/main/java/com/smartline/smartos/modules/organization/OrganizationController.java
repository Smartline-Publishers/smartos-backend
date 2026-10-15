package com.smartline.smartos.modules.organization;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.modules.organization.Organization.Plan;
import com.smartline.smartos.modules.organization.OrganizationDtos.CreateOrganizationRequest;
import com.smartline.smartos.modules.organization.OrganizationDtos.OrganizationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

  private final OrganizationService service;

  public OrganizationController(OrganizationService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORG_ADMIN')")
  public ApiResponse<List<OrganizationResponse>> list() {
    return ApiResponse.ok(service.list().stream().map(OrganizationResponse::from).toList());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORG_ADMIN')")
  public ApiResponse<OrganizationResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(OrganizationResponse.from(service.get(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORG_ADMIN')")
  public ApiResponse<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest req) {
    return ApiResponse.ok(OrganizationResponse.from(service.create(req.getName(), req.getSlug())));
  }

  @GetMapping("/current")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<OrganizationResponse> current() {
    return ApiResponse.ok(OrganizationResponse.from(service.current()));
  }

  @PatchMapping("/current")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public ApiResponse<OrganizationResponse> updateCurrent(
      @RequestBody OrganizationDtos.UpdateOrganizationRequest req) {
    return ApiResponse.ok(OrganizationResponse.from(service.updateCurrent(req)));
  }

  @DeleteMapping("/current/theme")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public void clearTheme() {
    service.clearTheme();
  }

  @PostMapping(value = "/current/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public ApiResponse<OrganizationResponse> uploadLogo(@RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(OrganizationResponse.from(service.uploadLogo(file)));
  }

  @PatchMapping("/{id}/plan")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ApiResponse<OrganizationResponse> changePlan(@PathVariable UUID id,
      @Valid @RequestBody ChangePlanRequest req) {
    return ApiResponse.ok(OrganizationResponse.from(service.changePlan(id, req.getPlan())));
  }

  public static class ChangePlanRequest {
    @NotNull private Plan plan;

    public ChangePlanRequest() {
    }

    public Plan getPlan() {
      return plan;
    }

    public void setPlan(Plan plan) {
      this.plan = plan;
    }
  }
}
