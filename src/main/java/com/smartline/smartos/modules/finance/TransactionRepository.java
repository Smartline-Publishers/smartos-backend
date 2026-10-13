package com.smartline.smartos.modules.finance;

import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
  Page<Transaction> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId,
      Pageable pageable);
  Page<Transaction> findByOrganizationIdAndTypeOrderByCreatedAtDesc(UUID organizationId,
      TransactionType type, Pageable pageable);
  Page<Transaction> findByOrganizationIdAndCategoryOrderByCreatedAtDesc(UUID organizationId,
      String category, Pageable pageable);
  List<Transaction> findByOrganizationIdAndTypeAndCreatedAtBetween(UUID organizationId,
      TransactionType type, Instant start, Instant end);
}
