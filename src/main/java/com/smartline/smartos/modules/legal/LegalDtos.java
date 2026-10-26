package com.smartline.smartos.modules.legal;

import com.smartline.smartos.modules.legal.LegalAcceptance.Context;
import com.smartline.smartos.modules.legal.LegalDocument.Code;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class LegalDtos {

  private LegalDtos() {
  }

  /** Public representation of a published legal document. */
  public static class LegalDocumentResponse {
    private UUID id;
    private Code code;
    private String version;
    private String title;
    private String summary;
    private LocalDate effectiveDate;
    private Map<String, Object> content;

    public LegalDocumentResponse() {
    }

    public static LegalDocumentResponse from(LegalDocument document) {
      LegalDocumentResponse response = new LegalDocumentResponse();
      response.id = document.getId();
      response.code = document.getCode();
      response.version = document.getVersion();
      response.title = document.getTitle();
      response.summary = document.getSummary();
      response.effectiveDate = document.getEffectiveDate();
      response.content = document.getContent();
      return response;
    }

    public UUID getId() {
      return id;
    }

    public Code getCode() {
      return code;
    }

    public String getVersion() {
      return version;
    }

    public String getTitle() {
      return title;
    }

    public String getSummary() {
      return summary;
    }

    public LocalDate getEffectiveDate() {
      return effectiveDate;
    }

    public Map<String, Object> getContent() {
      return content;
    }
  }

  /** Audit row returned to the user and to organisation administrators. */
  public static class LegalAcceptanceResponse {
    private UUID id;
    private UUID userId;
    private UUID organizationId;
    private Code documentCode;
    private String documentVersion;
    private String contentHash;
    private Context context;
    private UUID inviteId;
    private Instant acceptedAt;

    public LegalAcceptanceResponse() {
    }

    public static LegalAcceptanceResponse from(LegalAcceptance acceptance) {
      LegalAcceptanceResponse response = new LegalAcceptanceResponse();
      response.id = acceptance.getId();
      response.userId = acceptance.getUserId();
      response.organizationId = acceptance.getOrganizationId();
      response.documentCode = acceptance.getDocumentCode();
      response.documentVersion = acceptance.getDocumentVersion();
      response.contentHash = acceptance.getContentHash();
      response.context = acceptance.getContext();
      response.inviteId = acceptance.getInviteId();
      response.acceptedAt = acceptance.getAcceptedAt();
      return response;
    }

    public UUID getId() {
      return id;
    }

    public UUID getUserId() {
      return userId;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public Code getDocumentCode() {
      return documentCode;
    }

    public String getDocumentVersion() {
      return documentVersion;
    }

    public String getContentHash() {
      return contentHash;
    }

    public Context getContext() {
      return context;
    }

    public UUID getInviteId() {
      return inviteId;
    }

    public Instant getAcceptedAt() {
      return acceptedAt;
    }
  }
}
