package com.smartline.smartos.modules.book;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, UUID> {
  Optional<Book> findByIdAndOrganizationId(UUID id, UUID organizationId);
  Page<Book> findByOrganizationId(UUID organizationId, Pageable pageable);
  Page<Book> findByOrganizationIdAndStatus(UUID organizationId, Book.BookStatus status, Pageable pageable);
  Page<Book> findByOrganizationIdAndTitleContainingIgnoreCase(UUID organizationId, String title, Pageable pageable);
  Page<Book> findByOrganizationIdAndStatusAndTitleContainingIgnoreCase(
      UUID organizationId, Book.BookStatus status, String title, Pageable pageable);
  boolean existsByOrganizationIdAndIsbn(UUID organizationId, String isbn);
  long countByOrganizationId(UUID organizationId);
}
