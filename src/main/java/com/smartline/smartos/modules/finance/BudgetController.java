package com.smartline.smartos.modules.finance;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.finance.FinanceDtos.BudgetResponse;
import com.smartline.smartos.modules.finance.FinanceDtos.CreateBudgetRequest;
import com.smartline.smartos.modules.finance.FinanceDtos.FinanceMapper;
import com.smartline.smartos.modules.finance.FinanceDtos.UpdateBudgetRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/budgets")
public class BudgetController {

  private final BudgetService service;

  public BudgetController(BudgetService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<PageResponse<BudgetResponse>> list(Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(pageable).map(b -> service.withActuals(b.getId()))));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<BudgetResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.withActuals(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','FINANCE')")
  public ApiResponse<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest req) {
    Budget created = service.create(req);
    return ApiResponse.ok(service.withActuals(created.getId()));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','FINANCE')")
  public ApiResponse<BudgetResponse> update(@PathVariable UUID id,
      @RequestBody UpdateBudgetRequest req) {
    Budget updated = service.update(id, req);
    return ApiResponse.ok(service.withActuals(updated.getId()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','FINANCE')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
