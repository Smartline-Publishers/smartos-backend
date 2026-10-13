package com.smartline.smartos.modules.book;

import com.smartline.smartos.modules.book.Book.BookStatus;
import com.smartline.smartos.modules.book.Contributor.ContributorType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BookDtos {

  private BookDtos() {
  }

  public static class CreateBookRequest {
    @NotBlank private String title;
    private String subtitle;
    private String isbn;
    private String imprint;
    private BookStatus status;
    private String language;
    private Integer pageCount;

    public CreateBookRequest() {
    }

    public CreateBookRequest(String title, String subtitle, String isbn, String imprint,
        BookStatus status, String language, Integer pageCount) {
      this.title = title;
      this.subtitle = subtitle;
      this.isbn = isbn;
      this.imprint = imprint;
      this.status = status;
      this.language = language;
      this.pageCount = pageCount;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getSubtitle() {
      return subtitle;
    }

    public void setSubtitle(String subtitle) {
      this.subtitle = subtitle;
    }

    public String getIsbn() {
      return isbn;
    }

    public void setIsbn(String isbn) {
      this.isbn = isbn;
    }

    public String getImprint() {
      return imprint;
    }

    public void setImprint(String imprint) {
      this.imprint = imprint;
    }

    public BookStatus getStatus() {
      return status;
    }

    public void setStatus(BookStatus status) {
      this.status = status;
    }

    public String getLanguage() {
      return language;
    }

    public void setLanguage(String language) {
      this.language = language;
    }

    public Integer getPageCount() {
      return pageCount;
    }

    public void setPageCount(Integer pageCount) {
      this.pageCount = pageCount;
    }
  }

  public static class UpdateBookRequest {
    private String title;
    private String subtitle;
    private String isbn;
    private String imprint;
    private BookStatus status;
    private String language;
    private Integer pageCount;

    public UpdateBookRequest() {
    }

    public UpdateBookRequest(String title, String subtitle, String isbn, String imprint,
        BookStatus status, String language, Integer pageCount) {
      this.title = title;
      this.subtitle = subtitle;
      this.isbn = isbn;
      this.imprint = imprint;
      this.status = status;
      this.language = language;
      this.pageCount = pageCount;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getSubtitle() {
      return subtitle;
    }

    public void setSubtitle(String subtitle) {
      this.subtitle = subtitle;
    }

    public String getIsbn() {
      return isbn;
    }

    public void setIsbn(String isbn) {
      this.isbn = isbn;
    }

    public String getImprint() {
      return imprint;
    }

    public void setImprint(String imprint) {
      this.imprint = imprint;
    }

    public BookStatus getStatus() {
      return status;
    }

    public void setStatus(BookStatus status) {
      this.status = status;
    }

    public String getLanguage() {
      return language;
    }

    public void setLanguage(String language) {
      this.language = language;
    }

    public Integer getPageCount() {
      return pageCount;
    }

    public void setPageCount(Integer pageCount) {
      this.pageCount = pageCount;
    }
  }

  public static class BookResponse {
    private UUID id;
    private UUID organizationId;
    private String title;
    private String subtitle;
    private String isbn;
    private String imprint;
    private BookStatus status;
    private String language;
    private Integer pageCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<BookLinkResponse> contributors;

    public BookResponse() {
    }

    public BookResponse(UUID id, UUID organizationId, String title, String subtitle, String isbn,
        String imprint, BookStatus status, String language, Integer pageCount, Instant createdAt,
        Instant updatedAt) {
      this.id = id;
      this.organizationId = organizationId;
      this.title = title;
      this.subtitle = subtitle;
      this.isbn = isbn;
      this.imprint = imprint;
      this.status = status;
      this.language = language;
      this.pageCount = pageCount;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getSubtitle() {
      return subtitle;
    }

    public void setSubtitle(String subtitle) {
      this.subtitle = subtitle;
    }

    public String getIsbn() {
      return isbn;
    }

    public void setIsbn(String isbn) {
      this.isbn = isbn;
    }

    public String getImprint() {
      return imprint;
    }

    public void setImprint(String imprint) {
      this.imprint = imprint;
    }

    public BookStatus getStatus() {
      return status;
    }

    public void setStatus(BookStatus status) {
      this.status = status;
    }

    public String getLanguage() {
      return language;
    }

    public void setLanguage(String language) {
      this.language = language;
    }

    public Integer getPageCount() {
      return pageCount;
    }

    public void setPageCount(Integer pageCount) {
      this.pageCount = pageCount;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
    }

    public List<BookLinkResponse> getContributors() {
      return contributors;
    }

    public void setContributors(List<BookLinkResponse> contributors) {
      this.contributors = contributors;
    }
  }

  public static class CreateContributorRequest {
    @NotBlank private String fullName;
    private ContributorType type;
    private String email;
    private String bio;

    public CreateContributorRequest() {
    }

    public CreateContributorRequest(String fullName, ContributorType type, String email, String bio) {
      this.fullName = fullName;
      this.type = type;
      this.email = email;
      this.bio = bio;
    }

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }

    public ContributorType getType() {
      return type;
    }

    public void setType(ContributorType type) {
      this.type = type;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getBio() {
      return bio;
    }

    public void setBio(String bio) {
      this.bio = bio;
    }
  }

  public static class ContributorResponse {
    private UUID id;
    private UUID organizationId;
    private String fullName;
    private ContributorType type;
    private String email;
    private String bio;

    public ContributorResponse() {
    }

    public ContributorResponse(UUID id, UUID organizationId, String fullName, ContributorType type,
        String email, String bio) {
      this.id = id;
      this.organizationId = organizationId;
      this.fullName = fullName;
      this.type = type;
      this.email = email;
      this.bio = bio;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }

    public ContributorType getType() {
      return type;
    }

    public void setType(ContributorType type) {
      this.type = type;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getBio() {
      return bio;
    }

    public void setBio(String bio) {
      this.bio = bio;
    }
  }

  public static class BookMapper {
    private BookMapper() {
    }

    public static BookResponse toResponse(Book b) {
      return toResponse(b, List.of());
    }

    public static BookResponse toResponse(Book b, List<BookLinkResponse> links) {
      UUID orgId = b.getOrganization() != null ? b.getOrganization().getId() : b.getOrganizationId();
      BookResponse r = new BookResponse(b.getId(), orgId, b.getTitle(), b.getSubtitle(), b.getIsbn(),
          b.getImprint(), b.getStatus(), b.getLanguage(), b.getPageCount(),
          b.getCreatedAt(), b.getUpdatedAt());
      r.setContributors(links != null ? links : List.of());
      return r;
    }

    public static BookLinkResponse toLinkResponse(BookContributor link, String fullName) {
      return new BookLinkResponse(
          link.getContributor() != null ? link.getContributor().getId() : null,
          fullName, link.getRole(), link.getRoyaltyPct());
    }

    public static ContributorResponse toResponse(Contributor c) {
      UUID orgId = c.getOrganization() != null ? c.getOrganization().getId() : c.getOrganizationId();
      return new ContributorResponse(c.getId(), orgId, c.getFullName(), c.getType(), c.getEmail(), c.getBio());
    }
  }

  public static class LinkContributorRequest {
    @NotNull private UUID contributorId;
    private String role;
    @DecimalMin(value = "0", message = "Royalty share must be between 0 and 100")
    @DecimalMax(value = "100", message = "Royalty share must be between 0 and 100")
    private Double royaltyPct;

    public LinkContributorRequest() {
    }

    public UUID getContributorId() {
      return contributorId;
    }

    public void setContributorId(UUID contributorId) {
      this.contributorId = contributorId;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public Double getRoyaltyPct() {
      return royaltyPct;
    }

    public void setRoyaltyPct(Double royaltyPct) {
      this.royaltyPct = royaltyPct;
    }
  }

  public static class BookLinkResponse {
    private UUID contributorId;
    private String fullName;
    private String role;
    private Double royaltyPct;

    public BookLinkResponse() {
    }

    public BookLinkResponse(UUID contributorId, String fullName, String role, Double royaltyPct) {
      this.contributorId = contributorId;
      this.fullName = fullName;
      this.role = role;
      this.royaltyPct = royaltyPct;
    }

    public UUID getContributorId() {
      return contributorId;
    }

    public void setContributorId(UUID contributorId) {
      this.contributorId = contributorId;
    }

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public Double getRoyaltyPct() {
      return royaltyPct;
    }

    public void setRoyaltyPct(Double royaltyPct) {
      this.royaltyPct = royaltyPct;
    }
  }
}
