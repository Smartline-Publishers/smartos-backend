package com.smartline.smartos.modules.book;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookContributorRepository extends JpaRepository<BookContributor, UUID> {
  List<BookContributor> findByBookId(UUID bookId);
  void deleteByBookId(UUID bookId);
}
