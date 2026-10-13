package com.smartline.smartos.modules.finance;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.finance.RoyaltyDtos.GenerateStatementRequest;
import com.smartline.smartos.modules.finance.RoyaltyDtos.RoyaltyMapper;
import com.smartline.smartos.modules.finance.RoyaltyDtos.RoyaltyStatementResponse;
import com.smartline.smartos.modules.finance.RoyaltyStatement.StatementStatus;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/royalties/statements")
public class RoyaltyStatementController {

  private final RoyaltyService service;

  public RoyaltyStatementController(RoyaltyService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<PageResponse<RoyaltyStatementResponse>> list(
      @RequestParam(required = false) StatementStatus status,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(status, Paging.capped(pageable)).map(RoyaltyMapper::toResponse)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<RoyaltyStatementResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(RoyaltyMapper.toResponse(service.get(id)));
  }

  @PostMapping("/generate")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','FINANCE')")
  public ApiResponse<RoyaltyStatementResponse> generate(
      @Valid @RequestBody GenerateStatementRequest req) {
    return ApiResponse.ok(RoyaltyMapper.toResponse(service.generate(req)));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','FINANCE')")
  public ApiResponse<RoyaltyStatementResponse> approve(@PathVariable UUID id) {
    return ApiResponse.ok(RoyaltyMapper.toResponse(service.approve(id)));
  }

  @PostMapping("/{id}/pay")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','FINANCE')")
  public ApiResponse<RoyaltyStatementResponse> pay(@PathVariable UUID id) {
    return ApiResponse.ok(RoyaltyMapper.toResponse(service.pay(id)));
  }
}
