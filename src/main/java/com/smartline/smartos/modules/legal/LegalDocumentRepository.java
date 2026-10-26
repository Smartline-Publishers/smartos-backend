package com.smartline.smartos.modules.legal;

import com.smartline.smartos.modules.legal.LegalDocument.Code;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

  List<LegalDocument> findByCurrentTrueOrderByCodeAsc();

  Optional<LegalDocument> findFirstByCodeAndCurrentTrueOrderByEffectiveDateDesc(Code code);

  Optional<LegalDocument> findByCodeAndVersion(Code code, String version);

  List<LegalDocument> findByCodeOrderByEffectiveDateDesc(Code code);
}
