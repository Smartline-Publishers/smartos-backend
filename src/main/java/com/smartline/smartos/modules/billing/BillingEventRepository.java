package com.smartline.smartos.modules.billing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEventRepository extends JpaRepository<BillingEvent, String> {
}
