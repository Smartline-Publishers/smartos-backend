package com.smartline.smartos.modules.organization;

import com.smartline.smartos.common.exception.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validation for the organization brand theme stored as JSONB on
 * {@code organizations.theme}. Shape: {@code {light: Mode, dark: Mode}} with
 * {@code Mode = {primary, shell, secondary, tertiary, sidebar}} hex colors.
 * Both modes are required so the brand never half-applies when the user
 * flips modes; sidebar text colors are derived from the sidebar background
 * automatically so any sidebar color stays readable.
 */
public final class OrgTheme {

  public static final List<String> MODES = List.of("light", "dark");
  public static final List<String> SLOTS =
      List.of("primary", "shell", "secondary", "tertiary", "sidebar");

  /** Minimum primary-on-shell contrast (WCAG large-text/UI-component floor). */
  private static final double MIN_CONTRAST = 3.0;

  private OrgTheme() {
  }

  public static boolean isBlank(Object theme) {
    return theme == null || (theme instanceof Map<?, ?> m && m.isEmpty());
  }

  public static Map<String, Object> validated(Object raw) {
    if (!(raw instanceof Map<?, ?> root)) {
      throw ApiException.badRequest("Theme must be an object with 'light' and 'dark' modes");
    }
    Map<String, Object> out = new HashMap<>();
    for (String mode : MODES) {
      Object node = root.get(mode);
      if (!(node instanceof Map<?, ?> map)) {
        throw ApiException.badRequest("Theme must define both 'light' and 'dark' modes");
      }
      Map<String, Object> clean = new HashMap<>();
      for (String slot : SLOTS) {
        Object value = map.get(slot);
        if (!(value instanceof String s) || !isHexColor(s.trim())) {
          throw ApiException.badRequest(
              "Theme '" + mode + "." + slot + "' must be a hex color like #dc2626");
        }
        clean.put(slot, s.trim());
      }
      double contrast = contrastRatio((String) clean.get("primary"), (String) clean.get("shell"));
      if (contrast < MIN_CONTRAST) {
        throw ApiException.badRequest("Theme '" + mode + "' primary color has insufficient "
            + "contrast against the shell background (need at least " + MIN_CONTRAST + ":1)");
      }
      out.put(mode, clean);
    }
    return out;
  }

  public static boolean isHexColor(String value) {
    return value != null && value.matches("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");
  }

  /** WCAG relative-luminance contrast ratio between two hex colors. */
  public static double contrastRatio(String hexA, String hexB) {
    double lumA = luminance(hexA);
    double lumB = luminance(hexB);
    double lighter = Math.max(lumA, lumB);
    double darker = Math.min(lumA, lumB);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static double luminance(String hex) {
    String h = hex.startsWith("#") ? hex.substring(1) : hex;
    if (h.length() == 3) {
      h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
    }
    double r = channel(Integer.parseInt(h.substring(0, 2), 16) / 255.0);
    double g = channel(Integer.parseInt(h.substring(2, 4), 16) / 255.0);
    double b = channel(Integer.parseInt(h.substring(4, 6), 16) / 255.0);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static double channel(double c) {
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }
}
