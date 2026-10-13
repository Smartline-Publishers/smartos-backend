package com.smartline.smartos.modules.billing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  Optional<Subscription> findByOrganizationId(UUID organizationId);
}
