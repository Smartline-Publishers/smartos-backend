package com.smartline.smartos.modules.organization;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.modules.organization.OrganizationDtos.CreateOrganizationRequest;
import com.smartline.smartos.modules.organization.OrganizationDtos.OrganizationResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
}
