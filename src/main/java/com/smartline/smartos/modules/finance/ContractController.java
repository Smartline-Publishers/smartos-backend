package com.smartline.smartos.modules.finance;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.book.ContributorRepository;
import com.smartline.smartos.modules.finance.RoyaltyDtos.ContractResponse;
import com.smartline.smartos.modules.finance.RoyaltyDtos.CreateContractRequest;
import com.smartline.smartos.modules.finance.RoyaltyDtos.RoyaltyMapper;
import com.smartline.smartos.tenant.TenantContext;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/royalties/contracts")
public class ContractController {

  private final ContractService service;
  private final ContributorRepository contributors;

  public ContractController(ContractService service, ContributorRepository contributors) {
    this.service = service;
    this.contributors = contributors;
  }

  private ContractResponse withName(Contract contract) {
    UUID orgId = TenantContext.require();
    String name = contract.getContributor() != null ? contract.getContributor().getFullName() : null;
    if (name == null && contract.getContributor() != null) {
      name = contributors.findByIdAndOrganization_Id(contract.getContributor().getId(), orgId)
          .map(c -> c.getFullName())
          .orElse(null);
    }
    return RoyaltyMapper.toResponse(contract, name);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_royalties.read')")
  public ApiResponse<PageResponse<ContractResponse>> list(Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(Paging.capped(pageable)).map(this::withName)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_royalties.read')")
  public ApiResponse<ContractResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(withName(service.get(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_royalties.write')")
  public ApiResponse<ContractResponse> create(@Valid @RequestBody CreateContractRequest req) {
    return ApiResponse.ok(withName(service.create(req)));
  }

  @PostMapping("/{id}/terminate")
  @PreAuthorize("hasAuthority('PERM_royalties.write')")
  public ApiResponse<ContractResponse> terminate(@PathVariable UUID id) {
    return ApiResponse.ok(withName(service.terminate(id)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_royalties.write')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
