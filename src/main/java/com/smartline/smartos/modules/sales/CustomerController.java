package com.smartline.smartos.modules.sales;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.sales.SalesDtos.CreateCustomerRequest;
import com.smartline.smartos.modules.sales.SalesDtos.CustomerMapper;
import com.smartline.smartos.modules.sales.SalesDtos.CustomerResponse;
import com.smartline.smartos.modules.sales.SalesDtos.UpdateCustomerRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_customers.read')")
  public ApiResponse<PageResponse<CustomerResponse>> list(
      @RequestParam(required = false) String search,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(search, Paging.capped(pageable)).map(CustomerMapper::toResponse)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_customers.read')")
  public ApiResponse<CustomerResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(CustomerMapper.toResponse(service.get(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_customers.write')")
  public ApiResponse<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest req) {
    return ApiResponse.ok(CustomerMapper.toResponse(service.create(req)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_customers.write')")
  public ApiResponse<CustomerResponse> update(@PathVariable UUID id,
      @RequestBody UpdateCustomerRequest req) {
    return ApiResponse.ok(CustomerMapper.toResponse(service.update(id, req)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_customers.write')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
