package com.smartline.smartos.modules.sales;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
  // Order lines are mapped to DTOs (book title) outside the service
  // transaction with open-in-view disabled: fetch the book eagerly here so
  // mapping never hits an uninitialized lazy proxy (LazyInitializationException).
  @EntityGraph(attributePaths = {"book"})
  List<OrderItem> findByOrderId(UUID orderId);
  boolean existsByBookId(UUID bookId);
}
