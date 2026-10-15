package com.smartline.smartos.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.common.exception.ErrorCode;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Image uploads (avatars, organization logos) to Cloudinary. The database
 * keeps only the hosted {@code secure_url}; bytes never touch Postgres.
 * Uploads are optional infrastructure: when Cloudinary is not configured the
 * upload endpoints answer 501 and URL-based images keep working.
 */
@Service
public class MediaService {

  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  /** Matches delivery URLs to recover the public id for deletion. */
  private static final Pattern DELIVERY_URL =
      Pattern.compile("^https://res\\.cloudinary\\.com/[^/]+/image/upload/(?:v\\d+/)?(.+?)(?:\\.[a-zA-Z0-9]+)?$");

  private final Cloudinary cloudinary;
  private final long maxBytes;

  @Autowired
  public MediaService(
      @Value("${app.cloudinary.cloud-name:}") String cloudName,
      @Value("${app.cloudinary.api-key:}") String apiKey,
      @Value("${app.cloudinary.api-secret:}") String apiSecret,
      @Value("${app.cloudinary.max-bytes:5242880}") long maxBytes) {
    this.maxBytes = maxBytes;
    if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
      this.cloudinary = null;
      log.warn("Cloudinary uploads disabled (CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET not all set)");
    } else {
      log.info("Cloudinary uploads enabled for cloud '{}'", cloudName.trim());
      this.cloudinary = new Cloudinary(ObjectUtils.asMap(
          "cloud_name", cloudName.trim(),
          "api_key", apiKey.trim(),
          "api_secret", apiSecret.trim(),
          "secure", true));
    }
  }

  /** Visible for tests. */
  MediaService(Cloudinary cloudinary, long maxBytes) {
    this.cloudinary = cloudinary;
    this.maxBytes = maxBytes;
  }

  public boolean isConfigured() {
    return cloudinary != null;
  }

  private void requireConfigured() {
    if (!isConfigured()) {
      throw new ApiException(HttpStatus.NOT_IMPLEMENTED, ErrorCode.NOT_CONFIGURED.getCode(),
          "Image uploads are not configured on this server");
    }
  }

  /**
   * Uploads an image file to {@code folder} and returns the hosted secure URL.
   * If {@code currentUrl} is a previously uploaded Cloudinary image it is
   * deleted afterwards (best effort: deletion failures are logged, never
   * fail the request).
   */
  public String replace(String currentUrl, MultipartFile file, String folder) {
    requireConfigured();
    validate(file);
    String secureUrl = upload(file, folder);
    deleteIfHosted(currentUrl);
    return secureUrl;
  }

  public String upload(MultipartFile file, String folder) {
    requireConfigured();
    validate(file);
    try {
      byte[] bytes = file.getBytes();
      @SuppressWarnings("unchecked")
      Map<String, Object> result = cloudinary.uploader().upload(bytes,
          ObjectUtils.asMap("folder", folder, "resource_type", "image"));
      Object url = result.get("secure_url");
      if (!(url instanceof String s) || s.isBlank()) {
        throw ApiException.badRequest("Image upload failed: empty response from provider");
      }
      return s;
    } catch (IOException e) {
      throw ApiException.badRequest("Image upload failed: " + e.getMessage());
    } catch (Exception e) {
      if (e instanceof ApiException api) {
        throw api;
      }
      // Provider authentication/configuration failures (unknown cloud,
      // wrong key/secret) are a server misconfiguration, not a bad file:
      // answer 501 so the message is actionable instead of blaming the upload.
      String detail = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
      if (detail.contains("invalid cloud") || detail.contains("unauthorized")
          || detail.contains("forbidden") || detail.contains("invalid api key")) {
        log.error("Cloudinary upload rejected by provider (check CLOUDINARY_* settings)", e);
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, ErrorCode.NOT_CONFIGURED.getCode(),
            "Image uploads are misconfigured on this server");
      }
      log.warn("Cloudinary upload failed", e);
      throw ApiException.badRequest("Image upload failed");
    }
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("Choose an image file to upload");
    }
    String contentType = file.getContentType();
    if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
      throw ApiException.badRequest("Only image files can be uploaded");
    }
    if (file.getSize() > maxBytes) {
      throw ApiException.badRequest(
          "Image is too large (max " + (maxBytes / 1024 / 1024) + "MB)");
    }
  }

  private void deleteIfHosted(String url) {
    String publicId = publicIdFromUrl(url);
    if (publicId == null) {
      return;
    }
    try {
      cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
    } catch (Exception e) {
      log.warn("Cloudinary delete failed for {}", publicId, e);
    }
  }

  /**
   * Recovers the public id from a Cloudinary delivery URL, or null when the
   * URL is not Cloudinary-hosted (external URLs and data URLs are left alone).
   */
  static String publicIdFromUrl(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    Matcher m = DELIVERY_URL.matcher(url.trim());
    return m.matches() ? m.group(1) : null;
  }
}
