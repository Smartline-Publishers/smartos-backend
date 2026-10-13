package com.smartline.smartos.modules.finance;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.finance.FinanceDtos.CreateTransactionRequest;
import com.smartline.smartos.modules.finance.FinanceDtos.FinanceMapper;
import com.smartline.smartos.modules.finance.FinanceDtos.TransactionResponse;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/transactions")
public class TransactionController {

  private final FinanceService service;

  public TransactionController(FinanceService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<PageResponse<TransactionResponse>> list(
      @RequestParam(required = false) TransactionType type,
      @RequestParam(required = false) String category,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(type, category, pageable).map(FinanceMapper::toResponse)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','FINANCE')")
  public ApiResponse<TransactionResponse> record(@Valid @RequestBody CreateTransactionRequest req) {
    return ApiResponse.ok(FinanceMapper.toResponse(service.recordManual(req)));
  }
}
