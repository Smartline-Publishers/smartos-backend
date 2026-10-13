package com.smartline.smartos.modules.sales;

import com.smartline.smartos.modules.sales.Order.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  Optional<Order> findByIdAndOrganizationId(UUID id, UUID organizationId);
  Page<Order> findByOrganizationId(UUID organizationId, Pageable pageable);
  Page<Order> findByOrganizationIdAndStatus(UUID organizationId, OrderStatus status,
      Pageable pageable);
  Page<Order> findByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId,
      Pageable pageable);
  boolean existsByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);
  List<Order> findByOrganizationIdAndStatus(UUID organizationId, OrderStatus status);
}
