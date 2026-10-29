package com.smartline.smartos.config;

import com.smartline.smartos.common.mail.EmailBrand;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Resolves the email brand from configuration once at startup.
 *
 * <p>All values are optional; {@link EmailBrand} supplies the SmartOS defaults
 * and validates colours/URLs. Keeping the resolution here (rather than in the
 * template) lets tests construct an {@code EmailBrand} without a Spring
 * context.
 */
@Configuration
public class EmailBrandConfig {

  @Bean
  public EmailBrand emailBrand(Environment env) {
    return new EmailBrand(
        text(env, "app.mail.brand.app-name", "SmartOS"),
        text(env, "app.mail.brand.company-name", "Smartline Publishers Ltd"),
        text(env, "app.mail.brand.eyebrow", "SMARTLINE PUBLISHERS"),
        text(env, "app.mail.brand.logo-url", ""),
        text(env, "app.mail.brand.primary-color", "#dc2626"),
        text(env, "app.mail.brand.accent-color", "#2563eb"),
        text(env, "app.frontend-url", "http://localhost:3000"),
        text(env, "app.mail.brand.support-email", ""),
        text(env, "app.mail.brand.support-phone", ""),
        text(env, "app.mail.brand.support-hours", ""),
        text(env, "app.mail.brand.footer-address", ""),
        text(env, "app.mail.brand.privacy-url", ""),
        text(env, "app.mail.brand.terms-url", ""),
        text(env, "app.mail.brand.help-url", ""));
  }

  private static String text(Environment env, String key, String fallback) {
    String value = env.getProperty(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
