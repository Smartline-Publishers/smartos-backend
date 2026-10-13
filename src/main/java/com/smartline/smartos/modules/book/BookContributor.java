package com.smartline.smartos.modules.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "book_contributors")
public class BookContributor {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "book_id", nullable = false)
  private Book book;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contributor_id", nullable = false)
  private Contributor contributor;

  @Column(nullable = false, length = 32)
  private String role = "AUTHOR";

  @Column(name = "royalty_pct")
  private Double royaltyPct;

  public BookContributor() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Book getBook() {
    return book;
  }

  public void setBook(Book book) {
    this.book = book;
  }

  public Contributor getContributor() {
    return contributor;
  }

  public void setContributor(Contributor contributor) {
    this.contributor = contributor;
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
