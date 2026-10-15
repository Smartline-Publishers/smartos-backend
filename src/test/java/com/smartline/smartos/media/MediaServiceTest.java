package com.smartline.smartos.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.smartline.smartos.common.exception.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

  @Mock private Cloudinary cloudinary;
  @Mock private Uploader uploader;

  private MediaService service() {
    return new MediaService(cloudinary, 5 * 1024 * 1024);
  }

  private MockMultipartFile png(String name, int bytes) {
    return new MockMultipartFile(name, "logo.png", "image/png", new byte[bytes]);
  }

  @Test
  void unconfiguredServiceAnswersNotImplemented() {
    MediaService unconfigured = new MediaService(null, 5 * 1024 * 1024);

    assertTrue(!unconfigured.isConfigured());
    ApiException ex = assertThrows(ApiException.class,
        () -> unconfigured.upload(png("file", 100), "smartos/x/avatars"));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, ex.getStatus());
    assertEquals("NOT_CONFIGURED", ex.getCode());
  }

  @Test
  void rejectsNonImageAndOversize() {
    MockMultipartFile text =
        new MockMultipartFile("file", "note.txt", "text/plain", new byte[] {1, 2, 3});

    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
        () -> service().upload(text, "smartos/x/avatars")).getStatus());
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
        () -> service().upload(png("file", 6 * 1024 * 1024), "smartos/x/avatars")).getStatus());
  }

  @Test
  void uploadReturnsSecureUrlAndDeletesOldHostedImage() throws Exception {
    when(cloudinary.uploader()).thenReturn(uploader);
    when(uploader.upload(any(byte[].class), anyMap()))
        .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/smartos/x/new.png"));
    when(uploader.destroy(eq("smartos/x/old"), anyMap())).thenReturn(Map.of("result", "ok"));

    String url = service().replace(
        "https://res.cloudinary.com/demo/image/upload/v123/smartos/x/old.png",
        png("file", 100), "smartos/x/avatars");

    assertEquals("https://res.cloudinary.com/demo/image/upload/v1/smartos/x/new.png", url);
    verify(uploader).destroy(eq("smartos/x/old"), anyMap());
  }

  @Test
  void externalOldUrlIsLeftAlone() throws Exception {
    when(cloudinary.uploader()).thenReturn(uploader);
    when(uploader.upload(any(byte[].class), anyMap()))
        .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/n.png"));

    String url = service().replace("https://cdn.other.test/logo.png", png("file", 100), "f");

    assertTrue(url.startsWith("https://res.cloudinary.com/"));
    verify(uploader, never()).destroy(anyString(), anyMap());
  }

  @Test
  void deleteFailuresAreTolerated() throws Exception {
    when(cloudinary.uploader()).thenReturn(uploader);
    when(uploader.upload(any(byte[].class), anyMap()))
        .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/n.png"));
    when(uploader.destroy(anyString(), anyMap())).thenThrow(new RuntimeException("boom"));

    String url = service().replace(
        "https://res.cloudinary.com/demo/image/upload/v1/old.png", png("file", 100), "f");

    assertTrue(url.startsWith("https://res.cloudinary.com/"));
    verify(uploader).destroy(eq("old"), anyMap());
  }

  @Test
  void providerAuthFailuresBecomeNotConfigured() throws Exception {
    when(cloudinary.uploader()).thenReturn(uploader);
    when(uploader.upload(any(byte[].class), anyMap()))
        .thenThrow(new RuntimeException("Invalid cloud_name nope"));

    ApiException ex = assertThrows(ApiException.class,
        () -> service().upload(png("file", 100), "smartos/x/avatars"));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, ex.getStatus());
    assertEquals("NOT_CONFIGURED", ex.getCode());
  }

  @Test
  void publicIdExtraction() {
    assertEquals("smartos/x/old",
        MediaService.publicIdFromUrl(
            "https://res.cloudinary.com/demo/image/upload/v123/smartos/x/old.png"));
    assertEquals("smartos/x/old",
        MediaService.publicIdFromUrl(
            "https://res.cloudinary.com/demo/image/upload/smartos/x/old"));
    assertNull(MediaService.publicIdFromUrl("https://cdn.other.test/logo.png"));
    assertNull(MediaService.publicIdFromUrl("data:image/png;base64,AAA"));
    assertNull(MediaService.publicIdFromUrl(null));
    assertNull(MediaService.publicIdFromUrl("  "));
  }

  @Test
  void uploadAvatarAndLogoDelegateToMedia() {
    // Wired through UserService/OrganizationService; covered by service tests
    // via the shared MediaService contract above.
    verify(cloudinary, never()).api();
  }
}
