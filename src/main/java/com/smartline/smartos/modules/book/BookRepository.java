package com.smartline.smartos.modules.book;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, UUID> {
  Optional<Book> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  Page<Book> findByOrganization_Id(UUID organizationId, Pageable pageable);
  Page<Book> findByOrganization_IdAndStatus(UUID organizationId, Book.BookStatus status, Pageable pageable);
  Page<Book> findByOrganization_IdAndTitleContainingIgnoreCase(UUID organizationId, String title, Pageable pageable);
  Page<Book> findByOrganization_IdAndStatusAndTitleContainingIgnoreCase(
      UUID organizationId, Book.BookStatus status, String title, Pageable pageable);
  boolean existsByOrganization_IdAndIsbn(UUID organizationId, String isbn);
  long countByOrganization_Id(UUID organizationId);
}
