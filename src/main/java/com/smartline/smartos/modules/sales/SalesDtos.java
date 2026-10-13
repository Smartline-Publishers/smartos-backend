package com.smartline.smartos.modules.sales;

import com.smartline.smartos.modules.sales.Customer.CustomerType;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SalesDtos {

  private SalesDtos() {
  }

  public static class CreateCustomerRequest {
    @NotBlank private String name;
    private CustomerType type;
    @Email private String email;
    private String phone;
    private String address;
    @DecimalMin(value = "0", message = "Credit limit must not be negative")
    private BigDecimal creditLimit;

    public CreateCustomerRequest() {
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public CustomerType getType() {
      return type;
    }

    public void setType(CustomerType type) {
      this.type = type;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getPhone() {
      return phone;
    }

    public void setPhone(String phone) {
      this.phone = phone;
    }

    public String getAddress() {
      return address;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public BigDecimal getCreditLimit() {
      return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
      this.creditLimit = creditLimit;
    }
  }

  public static class UpdateCustomerRequest {
    private String name;
    private CustomerType type;
    @Email private String email;
    private String phone;
    private String address;
    @DecimalMin(value = "0", message = "Credit limit must not be negative")
    private BigDecimal creditLimit;

    public UpdateCustomerRequest() {
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public CustomerType getType() {
      return type;
    }

    public void setType(CustomerType type) {
      this.type = type;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getPhone() {
      return phone;
    }

    public void setPhone(String phone) {
      this.phone = phone;
    }

    public String getAddress() {
      return address;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public BigDecimal getCreditLimit() {
      return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
      this.creditLimit = creditLimit;
    }
  }

  public static class CustomerResponse {
    private UUID id;
    private UUID organizationId;
    private String name;
    private CustomerType type;
    private String email;
    private String phone;
    private String address;
    private BigDecimal creditLimit;

    public CustomerResponse() {
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public CustomerType getType() {
      return type;
    }

    public void setType(CustomerType type) {
      this.type = type;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getPhone() {
      return phone;
    }

    public void setPhone(String phone) {
      this.phone = phone;
    }

    public String getAddress() {
      return address;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public BigDecimal getCreditLimit() {
      return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
      this.creditLimit = creditLimit;
    }
  }

  public static class OrderItemRequest {
    @NotNull private UUID bookId;
    @NotNull @Min(1) private Integer quantity;
    @NotNull @DecimalMin(value = "0", message = "Unit price must not be negative")
    private BigDecimal unitPrice;

    public OrderItemRequest() {
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public Integer getQuantity() {
      return quantity;
    }

    public void setQuantity(Integer quantity) {
      this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
      return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
      this.unitPrice = unitPrice;
    }
  }

  public static class CreateOrderRequest {
    @NotNull private UUID customerId;
    @NotEmpty private List<@Valid OrderItemRequest> items;

    public CreateOrderRequest() {
    }

    public UUID getCustomerId() {
      return customerId;
    }

    public void setCustomerId(UUID customerId) {
      this.customerId = customerId;
    }

    public List<OrderItemRequest> getItems() {
      return items;
    }

    public void setItems(List<OrderItemRequest> items) {
      this.items = items;
    }
  }

  public static class FulfilOrderRequest {
    @NotNull private UUID warehouseId;

    public FulfilOrderRequest() {
    }

    public UUID getWarehouseId() {
      return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
      this.warehouseId = warehouseId;
    }
  }

  public static class OrderItemResponse {
    private UUID id;
    private UUID bookId;
    private String bookTitle;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public OrderItemResponse() {
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public String getBookTitle() {
      return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
      this.bookTitle = bookTitle;
    }

    public int getQuantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
      return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
      this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
      return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
      this.lineTotal = lineTotal;
    }
  }

  public static class OrderResponse {
    private UUID id;
    private UUID organizationId;
    private UUID customerId;
    private String customerName;
    private OrderStatus status;
    private BigDecimal total;
    private Instant createdAt;
    private Instant updatedAt;
    private List<OrderItemResponse> items;

    public OrderResponse() {
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public UUID getCustomerId() {
      return customerId;
    }

    public void setCustomerId(UUID customerId) {
      this.customerId = customerId;
    }

    public String getCustomerName() {
      return customerName;
    }

    public void setCustomerName(String customerName) {
      this.customerName = customerName;
    }

    public OrderStatus getStatus() {
      return status;
    }

    public void setStatus(OrderStatus status) {
      this.status = status;
    }

    public BigDecimal getTotal() {
      return total;
    }

    public void setTotal(BigDecimal total) {
      this.total = total;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
    }

    public List<OrderItemResponse> getItems() {
      return items;
    }

    public void setItems(List<OrderItemResponse> items) {
      this.items = items;
    }
  }

  public static class CustomerMapper {
    private CustomerMapper() {
    }

    public static CustomerResponse toResponse(Customer c) {
      UUID orgId = c.getOrganization() != null ? c.getOrganization().getId()
          : c.getOrganizationId();
      CustomerResponse r = new CustomerResponse();
      r.setId(c.getId());
      r.setOrganizationId(orgId);
      r.setName(c.getName());
      r.setType(c.getType());
      r.setEmail(c.getEmail());
      r.setPhone(c.getPhone());
      r.setAddress(c.getAddress());
      r.setCreditLimit(c.getCreditLimit());
      return r;
    }
  }

  public static class OrderMapper {
    private OrderMapper() {
    }

    public static OrderItemResponse toItemResponse(OrderItem i) {
      OrderItemResponse r = new OrderItemResponse();
      r.setId(i.getId());
      r.setBookId(i.getBook() != null ? i.getBook().getId() : null);
      r.setBookTitle(i.getBook() != null ? i.getBook().getTitle() : null);
      r.setQuantity(i.getQuantity());
      r.setUnitPrice(i.getUnitPrice());
      r.setLineTotal(i.getUnitPrice().multiply(java.math.BigDecimal.valueOf(i.getQuantity())));
      return r;
    }

    public static OrderResponse toResponse(Order o, List<OrderItemResponse> items) {
      UUID orgId = o.getOrganization() != null ? o.getOrganization().getId()
          : o.getOrganizationId();
      OrderResponse r = new OrderResponse();
      r.setId(o.getId());
      r.setOrganizationId(orgId);
      r.setCustomerId(o.getCustomer() != null ? o.getCustomer().getId() : null);
      r.setCustomerName(o.getCustomer() != null ? o.getCustomer().getName() : null);
      r.setStatus(o.getStatus());
      r.setTotal(o.getTotal());
      r.setCreatedAt(o.getCreatedAt());
      r.setUpdatedAt(o.getUpdatedAt());
      r.setItems(items != null ? items : List.of());
      return r;
    }
  }
}
