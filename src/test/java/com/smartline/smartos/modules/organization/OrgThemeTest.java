package com.smartline.smartos.modules.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartline.smartos.common.exception.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrgThemeTest {

  private static Map<String, Object> mode(String primary, String shell) {
    return Map.of("primary", primary, "shell", shell, "secondary", "#64748b",
        "tertiary", "#2563eb", "sidebar", "#0a0a0a");
  }

  @Test
  void validThemePassesThrough() {
    Map<String, Object> theme =
        Map.of("light", mode("#dc2626", "#f5f7fb"), "dark", mode("#f87171", "#0a0a0a"));

    Map<String, Object> clean = OrgTheme.validated(theme);

    assertEquals("#dc2626", ((Map<?, ?>) clean.get("light")).get("primary"));
  }

  @Test
  void missingModeIsRejected() {
    ApiException ex = assertThrows(ApiException.class,
        () -> OrgTheme.validated(Map.of("light", mode("#dc2626", "#f5f7fb"))));

    assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  void nonHexColorIsRejected() {
    Map<String, Object> theme =
        Map.of("light", mode("red", "#f5f7fb"), "dark", mode("#f87171", "#0a0a0a"));

    assertThrows(ApiException.class, () -> OrgTheme.validated(theme));
  }

  @Test
  void lowContrastPrimaryIsRejected() {
    // Near-identical primary/shell: contrast ~1.
    Map<String, Object> theme =
        Map.of("light", mode("#f5f7fb", "#f5f7fb"), "dark", mode("#f87171", "#0a0a0a"));

    assertThrows(ApiException.class, () -> OrgTheme.validated(theme));
  }

  @Test
  void blackOnWhiteContrastIsHigh() {
    assertTrue(OrgTheme.contrastRatio("#000000", "#ffffff") > 15);
  }
}
