package com.smartline.smartos.modules.inventory;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.modules.inventory.InventoryDtos.CreateWarehouseRequest;
import com.smartline.smartos.modules.inventory.InventoryDtos.InventoryMapper;
import com.smartline.smartos.modules.inventory.InventoryDtos.WarehouseResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

  private final InventoryService service;

  public WarehouseController(InventoryService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_warehouses.read')")
  public ApiResponse<List<WarehouseResponse>> list() {
    return ApiResponse.ok(service.listWarehouses().stream()
        .map(InventoryMapper::toResponse)
        .toList());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_warehouses.read')")
  public ApiResponse<WarehouseResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(InventoryMapper.toResponse(service.getWarehouse(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_warehouses.write')")
  public ApiResponse<WarehouseResponse> create(@Valid @RequestBody CreateWarehouseRequest req) {
    return ApiResponse.ok(InventoryMapper.toResponse(service.createWarehouse(req)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_warehouses.write')")
  public void delete(@PathVariable UUID id) {
    service.deleteWarehouse(id);
  }
}
