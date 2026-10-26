package com.smartline.smartos.modules.legal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.legal.LegalAcceptance.Context;
import com.smartline.smartos.modules.legal.LegalDocument.Code;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class LegalServiceTest {

  @Mock private LegalDocumentRepository documents;
  @Mock private LegalAcceptanceRepository acceptances;

  private LegalService service;
  private LegalDocument terms;
  private LegalDocument privacy;

  @BeforeEach
  void setUp() {
    service = new LegalService(documents, acceptances);
    terms = document(Code.TERMS_OF_SERVICE, "1.0", "Terms of Service");
    privacy = document(Code.PRIVACY_POLICY, "1.0", "Privacy Policy");
  }

  @Test
  void currentDocumentsReturnsPublishedVersions() {
    when(documents.findByCurrentTrueOrderByCodeAsc()).thenReturn(List.of(privacy, terms));

    var result = service.currentDocuments();

    assertEquals(2, result.size());
    assertEquals(Code.PRIVACY_POLICY, result.get(0).getCode());
    assertEquals("1.0", result.get(0).getVersion());
  }

  @Test
  void missingAcceptancesAreRejectedWithFieldErrors() {
    stubCurrentDocuments();

    ApiException ex = assertThrows(ApiException.class,
        () -> service.requireAndRecord(UUID.randomUUID(), UUID.randomUUID(), null, "  ",
            Context.REGISTRATION, null, null, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertEquals("LEGAL_ACCEPTANCE_REQUIRED", ex.getCode());
    assertTrue(ex.getFields().containsKey("termsVersion"));
    assertTrue(ex.getFields().containsKey("privacyVersion"));
    verify(acceptances, never()).save(any(LegalAcceptance.class));
  }

  @Test
  void staleVersionIsRejected() {
    stubCurrentDocuments();

    ApiException ex = assertThrows(ApiException.class,
        () -> service.requireAndRecord(UUID.randomUUID(), UUID.randomUUID(), "0.9", "1.0",
            Context.INVITE_ACCEPTANCE, UUID.randomUUID(), "127.0.0.1", "JUnit"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertTrue(ex.getFields().containsKey("termsVersion"));
    assertFalse(ex.getFields().containsKey("privacyVersion"));
  }

  @Test
  void acceptedVersionsAreRecordedForBothDocuments() {
    stubCurrentDocuments();
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inviteId = UUID.randomUUID();

    service.requireAndRecord(userId, orgId, "1.0", "1.0", Context.INVITE_ACCEPTANCE, inviteId,
        "203.0.113.9", "Mozilla/5.0");

    ArgumentCaptor<LegalAcceptance> captor = ArgumentCaptor.forClass(LegalAcceptance.class);
    verify(acceptances, org.mockito.Mockito.times(2)).save(captor.capture());
    List<LegalAcceptance> saved = captor.getAllValues();
    assertEquals(Code.TERMS_OF_SERVICE, saved.get(0).getDocumentCode());
    assertEquals(Code.PRIVACY_POLICY, saved.get(1).getDocumentCode());
    for (LegalAcceptance acceptance : saved) {
      assertEquals(userId, acceptance.getUserId());
      assertEquals(orgId, acceptance.getOrganizationId());
      assertEquals("1.0", acceptance.getDocumentVersion());
      assertEquals(Context.INVITE_ACCEPTANCE, acceptance.getContext());
      assertEquals(inviteId, acceptance.getInviteId());
      assertEquals("203.0.113.9", acceptance.getIpAddress());
      assertEquals("Mozilla/5.0", acceptance.getUserAgent());
      assertEquals(terms.getContentHash().length(), acceptance.getContentHash().length());
    }
  }

  private void stubCurrentDocuments() {
    when(documents.findFirstByCodeAndCurrentTrueOrderByEffectiveDateDesc(Code.TERMS_OF_SERVICE))
        .thenReturn(Optional.of(terms));
    when(documents.findFirstByCodeAndCurrentTrueOrderByEffectiveDateDesc(Code.PRIVACY_POLICY))
        .thenReturn(Optional.of(privacy));
  }

  private LegalDocument document(Code code, String version, String title) {
    LegalDocument document = new LegalDocument();
    document.setId(UUID.randomUUID());
    document.setCode(code);
    document.setVersion(version);
    document.setTitle(title);
    document.setEffectiveDate(LocalDate.of(2026, 10, 26));
    document.setContent(Map.of("sections", List.of()));
    document.setContentHash("a".repeat(64));
    document.setCurrent(true);
    return document;
  }
}
