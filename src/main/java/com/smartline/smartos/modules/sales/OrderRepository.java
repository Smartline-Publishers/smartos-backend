package com.smartline.smartos.modules.sales;

import com.smartline.smartos.modules.sales.Order.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  // The controller maps orders to DTOs (customer name) outside the service
  // transaction with open-in-view disabled: fetch the customer eagerly here so
  // mapping never hits an uninitialized lazy proxy (LazyInitializationException).
  @EntityGraph(attributePaths = {"customer"})
  Optional<Order> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  @EntityGraph(attributePaths = {"customer"})
  Page<Order> findByOrganization_Id(UUID organizationId, Pageable pageable);
  @EntityGraph(attributePaths = {"customer"})
  Page<Order> findByOrganization_IdAndStatus(UUID organizationId, OrderStatus status,
      Pageable pageable);
  @EntityGraph(attributePaths = {"customer"})
  Page<Order> findByOrganization_IdAndCustomerId(UUID organizationId, UUID customerId,
      Pageable pageable);
  boolean existsByOrganization_IdAndCustomerId(UUID organizationId, UUID customerId);
  List<Order> findByOrganization_IdAndStatus(UUID organizationId, OrderStatus status);
  long countByOrganization_IdAndStatusIn(UUID organizationId, Collection<OrderStatus> statuses);
  long countByOrganization_IdAndCustomerId(UUID organizationId, UUID customerId);
}
