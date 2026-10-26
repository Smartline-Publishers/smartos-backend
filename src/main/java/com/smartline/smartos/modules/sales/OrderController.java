package com.smartline.smartos.modules.sales;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.SalesDtos.CreateOrderRequest;
import com.smartline.smartos.modules.sales.SalesDtos.FulfilOrderRequest;
import com.smartline.smartos.modules.sales.SalesDtos.OrderMapper;
import com.smartline.smartos.modules.sales.SalesDtos.OrderResponse;
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
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final OrderService service;

  public OrderController(OrderService service) {
    this.service = service;
  }

  private OrderResponse withItems(Order order) {
    return OrderMapper.toResponse(order, service.orderItems(order.getId()).stream()
        .map(OrderMapper::toItemResponse)
        .toList());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_orders.read')")
  public ApiResponse<PageResponse<OrderResponse>> list(
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) UUID customerId,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(status, customerId, Paging.capped(pageable)).map(this::withItems)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_orders.read')")
  public ApiResponse<OrderResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(withItems(service.get(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_orders.write')")
  public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
    Order order = service.create(req);
    return ApiResponse.ok(withItems(order));
  }

  @PostMapping("/{id}/confirm")
  @PreAuthorize("hasAuthority('PERM_orders.write')")
  public ApiResponse<OrderResponse> confirm(@PathVariable UUID id) {
    return ApiResponse.ok(withItems(service.confirm(id)));
  }

  @PostMapping("/{id}/fulfil")
  @PreAuthorize("hasAuthority('PERM_orders.write')")
  public ApiResponse<OrderResponse> fulfil(@PathVariable UUID id,
      @Valid @RequestBody FulfilOrderRequest req) {
    return ApiResponse.ok(withItems(service.fulfil(id, req.getWarehouseId())));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAuthority('PERM_orders.write')")
  public ApiResponse<OrderResponse> cancel(@PathVariable UUID id) {
    return ApiResponse.ok(withItems(service.cancel(id)));
  }
}
