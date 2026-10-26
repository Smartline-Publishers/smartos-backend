package com.smartline.smartos.modules.legal;

import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.common.exception.ErrorCode;
import com.smartline.smartos.modules.legal.LegalAcceptance.Context;
import com.smartline.smartos.modules.legal.LegalDtos.LegalAcceptanceResponse;
import com.smartline.smartos.modules.legal.LegalDtos.LegalDocumentResponse;
import com.smartline.smartos.modules.legal.LegalDocument.Code;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalService {

  private final LegalDocumentRepository documents;
  private final LegalAcceptanceRepository acceptances;

  public LegalService(LegalDocumentRepository documents, LegalAcceptanceRepository acceptances) {
    this.documents = documents;
    this.acceptances = acceptances;
  }

  /** The versions shown on the public legal page and in the consent gate. */
  @Transactional(readOnly = true)
  public List<LegalDocumentResponse> currentDocuments() {
    return documents.findByCurrentTrueOrderByCodeAsc().stream()
        .map(LegalDocumentResponse::from)
        .toList();
  }

  /**
   * Validates that the caller accepted the current versions, then records the
   * evidence. Both checks happen even when the first one fails so the client
   * can highlight every unchecked/stale document at once.
   */
  @Transactional
  public void requireAndRecord(UUID userId, UUID organizationId, String termsVersion,
      String privacyVersion, Context context, UUID inviteId, String ipAddress, String userAgent) {
    LegalDocument terms = currentOrThrow(Code.TERMS_OF_SERVICE);
    LegalDocument privacy = currentOrThrow(Code.PRIVACY_POLICY);

    Map<String, String> fields = new LinkedHashMap<>();
    validateVersion(fields, "termsVersion", terms, termsVersion);
    validateVersion(fields, "privacyVersion", privacy, privacyVersion);
    if (!fields.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST,
          ErrorCode.LEGAL_ACCEPTANCE_REQUIRED.getCode(),
          "Review and accept the current legal documents to continue.", fields);
    }

    record(userId, organizationId, terms, context, inviteId, ipAddress, userAgent);
    record(userId, organizationId, privacy, context, inviteId, ipAddress, userAgent);
  }

  @Transactional(readOnly = true)
  public List<LegalAcceptanceResponse> acceptancesForUser(UUID userId) {
    return acceptances.findByUserIdOrderByAcceptedAtDesc(userId).stream()
        .map(LegalAcceptanceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<LegalAcceptanceResponse> acceptancesForOrganization(UUID organizationId,
      Pageable pageable) {
    return PageResponse.from(acceptances
        .findByOrganizationIdOrderByAcceptedAtDesc(organizationId, pageable)
        .map(LegalAcceptanceResponse::from));
  }

  private void validateVersion(Map<String, String> fields, String field, LegalDocument current,
      String accepted) {
    if (accepted == null || accepted.isBlank()) {
      fields.put(field, "Accept the " + current.getTitle() + " to continue.");
    } else if (!current.getVersion().equals(accepted.trim())) {
      fields.put(field, "The " + current.getTitle() + " changed. Review and accept version "
          + current.getVersion() + ".");
    }
  }

  private LegalDocument currentOrThrow(Code code) {
    return documents.findFirstByCodeAndCurrentTrueOrderByEffectiveDateDesc(code)
        .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCode.NOT_CONFIGURED.getCode(),
            "Legal documents are unavailable. Please contact support."));
  }

  private void record(UUID userId, UUID organizationId, LegalDocument document, Context context,
      UUID inviteId, String ipAddress, String userAgent) {
    LegalAcceptance acceptance = new LegalAcceptance();
    acceptance.setUserId(userId);
    acceptance.setOrganizationId(organizationId);
    acceptance.setDocumentId(document.getId());
    acceptance.setDocumentCode(document.getCode());
    acceptance.setDocumentVersion(document.getVersion());
    acceptance.setContentHash(document.getContentHash());
    acceptance.setContext(context);
    acceptance.setInviteId(inviteId);
    acceptance.setIpAddress(truncate(ipAddress, 64));
    acceptance.setUserAgent(truncate(userAgent, 512));
    acceptances.save(acceptance);
  }

  private static String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }
}
