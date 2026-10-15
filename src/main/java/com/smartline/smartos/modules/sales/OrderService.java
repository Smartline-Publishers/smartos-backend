package com.smartline.smartos.modules.sales;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.finance.Transaction;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.finance.TransactionRepository;
import com.smartline.smartos.modules.inventory.InventoryService;
import com.smartline.smartos.modules.inventory.StockMovement.MovementType;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.SalesDtos.CreateOrderRequest;
import com.smartline.smartos.modules.sales.SalesDtos.OrderItemRequest;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final CustomerRepository customers;
  private final BookRepository books;
  private final OrganizationRepository organizations;
  private final InventoryService inventory;
  private final TransactionRepository transactions;

  public OrderService(OrderRepository orders, OrderItemRepository items,
      CustomerRepository customers, BookRepository books, OrganizationRepository organizations,
      InventoryService inventory, TransactionRepository transactions) {
    this.orders = orders;
    this.items = items;
    this.customers = customers;
    this.books = books;
    this.organizations = organizations;
    this.inventory = inventory;
    this.transactions = transactions;
  }

  @Transactional
  @Audited(action = "order.create", entityType = "Order")
  public Order create(CreateOrderRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    Customer customer = customers.findByIdAndOrganization_Id(req.getCustomerId(), orgId)
        .orElseThrow(() -> ApiException.notFound("Customer", req.getCustomerId()));
    Order order = new Order();
    order.setOrganization(org);
    order.setCustomer(customer);
    order.setStatus(OrderStatus.DRAFT);
    order.setTotal(BigDecimal.ZERO);
    Order saved = orders.save(order);
    BigDecimal total = BigDecimal.ZERO;
    for (OrderItemRequest itemReq : req.getItems()) {
      Book book = books.findByIdAndOrganization_Id(itemReq.getBookId(), orgId)
          .orElseThrow(() -> ApiException.notFound("Book", itemReq.getBookId()));
      OrderItem item = new OrderItem();
      item.setOrder(saved);
      item.setBook(book);
      item.setQuantity(itemReq.getQuantity());
      item.setUnitPrice(itemReq.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
      items.save(item);
      total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
    saved.setTotal(total.setScale(2, RoundingMode.HALF_UP));
    return orders.save(saved);
  }

  @Transactional(readOnly = true)
  public Page<Order> list(OrderStatus status, UUID customerId, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (status != null) {
      return orders.findByOrganization_IdAndStatus(orgId, status, pageable);
    }
    if (customerId != null) {
      return orders.findByOrganization_IdAndCustomerId(orgId, customerId, pageable);
    }
    return orders.findByOrganization_Id(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public Order get(UUID id) {
    return orders.findByIdAndOrganization_Id(id, TenantContext.require())
        .orElseThrow(() -> ApiException.notFound("Order", id));
  }

  @Transactional(readOnly = true)
  public List<OrderItem> orderItems(UUID orderId) {
    Order order = get(orderId);
    return items.findByOrderId(order.getId());
  }

  @Transactional
  @Audited(action = "order.confirm", entityType = "Order")
  public Order confirm(UUID id) {
    Order order = get(id);
    if (order.getStatus() != OrderStatus.DRAFT) {
      throw ApiException.badRequest("Only draft orders can be confirmed");
    }
    if (items.findByOrderId(order.getId()).isEmpty()) {
      throw ApiException.badRequest("Cannot confirm an order with no items");
    }
    order.setStatus(OrderStatus.CONFIRMED);
    return orders.save(order);
  }

  /**
   * Fulfils an order: dispatches every line from stock and posts the sale.
   * One transaction — a stock shortfall rolls back the whole fulfilment,
   * so stock and revenue can never drift apart.
   */
  @Transactional
  @Audited(action = "order.fulfil", entityType = "Order")
  public Order fulfil(UUID id, UUID warehouseId) {
    Order order = get(id);
    if (order.getStatus() != OrderStatus.CONFIRMED) {
      throw ApiException.badRequest("Only confirmed orders can be fulfilled");
    }
    List<OrderItem> lines = items.findByOrderId(order.getId());
    if (lines.isEmpty()) {
      throw ApiException.badRequest("Cannot fulfil an order with no items");
    }
    List<OrderItem> sorted = new ArrayList<>(lines);
    sorted.sort((a, b) -> a.getBook().getId().compareTo(b.getBook().getId()));
    for (OrderItem item : sorted) {
      inventory.recordMovement(item.getBook().getId(), warehouseId, -item.getQuantity(),
          MovementType.OUT, "ORDER", order.getId(), "Order fulfilment");
    }
    Transaction sale = new Transaction();
    sale.setOrganization(order.getOrganization());
    sale.setType(TransactionType.SALE);
    sale.setAmount(order.getTotal());
    sale.setCategory("SALES");
    sale.setReferenceType("ORDER");
    sale.setReferenceId(order.getId());
    sale.setDescription("Order fulfilled");
    transactions.save(sale);
    order.setStatus(OrderStatus.FULFILLED);
    return orders.save(order);
  }

  @Transactional
  @Audited(action = "order.cancel", entityType = "Order")
  public Order cancel(UUID id) {
    Order order = get(id);
    if (order.getStatus() != OrderStatus.DRAFT && order.getStatus() != OrderStatus.CONFIRMED) {
      throw ApiException.badRequest("Only draft or confirmed orders can be cancelled");
    }
    order.setStatus(OrderStatus.CANCELLED);
    return orders.save(order);
  }
}
