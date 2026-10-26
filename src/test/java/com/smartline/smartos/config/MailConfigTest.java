package com.smartline.smartos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailConfigTest {

  @Test
  void bindsProvidedSmtpEnvironmentNamesAndUsesStartTlsOnPort587() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("MAIL_ENABLED", "true")
        .withProperty("EMAIL_PROVIDER", "")
        .withProperty("SMTP_SERVER", "smtp.example.com")
        .withProperty("SMTP_PORT", "587")
        .withProperty("SMTP_EMAIL", "mailer@example.com")
        .withProperty("SMTP_PASSWORD", "smtp-secret")
        .withProperty("SMTP_USE_SSL", "false");
    MailConfig config = new MailConfig(environment);

    MailConfig.Settings settings = config.settings();
    JavaMailSenderImpl sender = (JavaMailSenderImpl) config.javaMailSender();
    Properties properties = sender.getJavaMailProperties();

    assertTrue(settings.enabled());
    assertEquals("smtp.example.com", settings.host());
    assertEquals(587, settings.port());
    assertEquals("mailer@example.com", settings.username());
    assertEquals("smtp-secret", settings.password());
    assertFalse(settings.implicitTls());
    assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"));
    assertEquals("true", properties.getProperty("mail.smtp.starttls.required"));
    assertEquals("false", properties.getProperty("mail.smtp.ssl.enable"));
  }

  @Test
  void providerPresetSuppliesHostAndPortWhenSmtpValuesAreBlank() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("MAIL_ENABLED", "true")
        .withProperty("EMAIL_PROVIDER", "  Gmail ")
        .withProperty("SMTP_SERVER", "")
        .withProperty("SMTP_PORT", "")
        .withProperty("SMTP_USE_SSL", "");
    MailConfig.Settings settings = new MailConfig(environment).settings();

    assertEquals("gmail", settings.provider());
    assertEquals("smtp.gmail.com", settings.host());
    assertEquals(587, settings.port());
    assertFalse(settings.implicitTls());
  }

  @Test
  void implicitTlsSelectsPort465WhenPresetPortIsNotExplicit() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("EMAIL_PROVIDER", "gmail")
        .withProperty("SMTP_PORT", "")
        .withProperty("SMTP_USE_SSL", "true");
    MailConfig.Settings settings = new MailConfig(environment).settings();

    assertEquals(465, settings.port());
    assertTrue(settings.implicitTls());
  }

  @Test
  void derivesImplicitTlsFromPort465AndSupportsLegacySpringMailNames() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("spring.mail.host", "legacy.example.com")
        .withProperty("spring.mail.port", "465")
        .withProperty("spring.mail.username", "legacy-user")
        .withProperty("spring.mail.password", "legacy-password");
    MailConfig.Settings settings = new MailConfig(environment).settings();

    assertEquals("legacy.example.com", settings.host());
    assertEquals(465, settings.port());
    assertEquals("legacy-user", settings.username());
    assertEquals("legacy-password", settings.password());
    assertTrue(settings.implicitTls());
  }

  @Test
  void rejectsMalformedOptionalSmtpSettingsWithSafeConfigurationErrors() {
    MockEnvironment invalidPort = new MockEnvironment()
        .withProperty("SMTP_PORT", "not-a-port");
    assertTrue(assertThrows(IllegalStateException.class,
        () -> new MailConfig(invalidPort).settings()).getMessage().contains("SMTP_PORT"));

    MockEnvironment invalidSsl = new MockEnvironment()
        .withProperty("SMTP_USE_SSL", "sometimes");
    assertTrue(assertThrows(IllegalStateException.class,
        () -> new MailConfig(invalidSsl).settings()).getMessage().contains("SMTP_USE_SSL"));
  }
}
