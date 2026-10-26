package com.smartline.smartos.modules.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartline.smartos.modules.legal.LegalDocument.Code;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the legal documents bundled with the application.
 *
 * <p>Runs outside the test profile (tests replace Flyway/JPA with H2). Existing
 * versions are never rewritten - corrections require a new version - but the
 * newest bundled version for each code is marked current so the consent gate
 * always points at the intended text.
 */
@Component
@Profile("!test")
public class LegalDocumentSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LegalDocumentSeeder.class);

  private final LegalDocumentRepository documents;
  private final ObjectMapper objectMapper;

  public LegalDocumentSeeder(LegalDocumentRepository documents, ObjectMapper objectMapper) {
    this.documents = documents;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    SeedFile seed = readSeedFile();
    if (seed.documents == null || seed.documents.isEmpty()) {
      throw new IllegalStateException("legal/documents.json contains no documents");
    }

    Map<Code, String> currentVersions = new EnumMap<>(Code.class);
    for (SeedDocument source : seed.documents) {
      Code code = Code.valueOf(source.code);
      String hash = hash(source.content);
      LegalDocument existing = documents.findByCodeAndVersion(code, source.version).orElse(null);
      if (existing == null) {
        documents.save(toEntity(source, code, hash));
        log.info("Published legal document {} version {}", code, source.version);
      } else if (!hash.equals(existing.getContentHash())) {
        // Never mutate a published version: an acceptance may already point
        // at it. Bump the version in documents.json instead.
        log.warn("Bundled content for {} v{} differs from the published version; keeping the "
            + "published text. Bump the version to publish changes.", code, source.version);
      }
      currentVersions.put(code, source.version);
    }

    for (Map.Entry<Code, String> entry : currentVersions.entrySet()) {
      List<LegalDocument> versions = new ArrayList<>(
          documents.findByCodeOrderByEffectiveDateDesc(entry.getKey()));
      // Clear first, flush, then set the new current row. Publication is guarded
      // by a partial unique index (one current version per code).
      versions.forEach(version -> version.setCurrent(false));
      documents.saveAllAndFlush(versions);
      versions.stream()
          .filter(version -> version.getVersion().equals(entry.getValue()))
          .findFirst()
          .ifPresent(version -> {
            version.setCurrent(true);
            documents.saveAndFlush(version);
          });
    }
  }

  private SeedFile readSeedFile() throws Exception {
    ClassPathResource resource = new ClassPathResource("legal/documents.json");
    if (!resource.exists()) {
      throw new IllegalStateException("Missing legal/documents.json on the classpath");
    }
    try (InputStream input = resource.getInputStream()) {
      return objectMapper.readValue(input, SeedFile.class);
    }
  }

  private LegalDocument toEntity(SeedDocument source, Code code, String hash) {
    LegalDocument document = new LegalDocument();
    document.setCode(code);
    document.setVersion(source.version);
    document.setTitle(source.title);
    document.setSummary(source.summary);
    document.setEffectiveDate(LocalDate.parse(source.effectiveDate));
    document.setContent(source.content);
    document.setContentHash(hash);
    document.setCurrent(false);
    return document;
  }

  private String hash(Map<String, Object> content) throws Exception {
    byte[] json = objectMapper.writeValueAsString(content)
        .getBytes(StandardCharsets.UTF_8);
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
  }

  static class SeedFile {
    public List<SeedDocument> documents;
  }

  static class SeedDocument {
    public String code;
    public String version;
    public String title;
    public String summary;
    public String effectiveDate;
    public Map<String, Object> content;
  }
}
