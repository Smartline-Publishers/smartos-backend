package com.smartline.smartos.modules.inventory;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.inventory.InventoryDtos.CreateMovementRequest;
import com.smartline.smartos.modules.inventory.InventoryDtos.InventoryMapper;
import com.smartline.smartos.modules.inventory.InventoryDtos.MovementResponse;
import com.smartline.smartos.modules.inventory.InventoryDtos.StockLevelResponse;
import com.smartline.smartos.modules.inventory.InventoryDtos.TransferRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/inventory")
public class InventoryController {

  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  @GetMapping("/movements")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<PageResponse<MovementResponse>> movements(
      @RequestParam(required = false) UUID bookId,
      @RequestParam(required = false) UUID warehouseId,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.listMovements(bookId, warehouseId, pageable).map(InventoryMapper::toResponse)));
  }

  @PostMapping("/movements")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','WAREHOUSE')")
  public ApiResponse<MovementResponse> record(@Valid @RequestBody CreateMovementRequest req) {
    return ApiResponse.ok(InventoryMapper.toResponse(service.recordMovement(req)));
  }

  @GetMapping("/levels")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<StockLevelResponse>> levels(
      @RequestParam(required = false) UUID bookId,
      @RequestParam(required = false) UUID warehouseId,
      @RequestParam(required = false) Integer below) {
    return ApiResponse.ok(service.levels(bookId, warehouseId, below));
  }

  @PostMapping("/transfers")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','WAREHOUSE')")
  public ApiResponse<List<MovementResponse>> transfer(@Valid @RequestBody TransferRequest req) {
    return ApiResponse.ok(service.transfer(req).stream()
        .map(InventoryMapper::toResponse)
        .toList());
  }
}
