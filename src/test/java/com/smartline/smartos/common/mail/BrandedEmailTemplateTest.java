package com.smartline.smartos.common.mail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartline.smartos.common.mail.BrandedEmailTemplate.RenderedEmail;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrandedEmailTemplateTest {

  private final BrandedEmailTemplate template =
      new BrandedEmailTemplate(EmailBrand.defaults());

  @Test
  void rendersResponsiveBrandedHtmlAndPlainTextAlternative() {
    RenderedEmail email = template.render(EmailContent.builder("Reset your password")
        .preheader("Reset your account password.")
        .greeting("Hello Alex,")
        .paragraphs(List.of("Use this link to continue."))
        .action("Reset password", "https://smartos.example/reset?token=abc&next=home")
        .securityNotice("Security notice", "This link expires in 30 minutes.")
        .build());

    assertTrue(email.html().contains("SmartOS"));
    assertTrue(email.html().contains("max-width:600px"));
    assertTrue(email.html().contains("@media (prefers-color-scheme: dark)"));
    assertTrue(email.html().contains("#2563eb"));
    assertTrue(email.html().contains("class=\"email-os\" style=\"color:#2563eb;\">OS</span>"));
    assertTrue(email.html().contains("bgcolor=\"#dc2626\""));
    assertTrue(email.html().contains("token=abc&amp;next=home"));
    assertTrue(email.html().contains("Reset password"));
    assertTrue(email.text().contains("Reset password:\n"
        + "https://smartos.example/reset?token=abc&next=home"));
    assertTrue(email.text().contains("This link expires in 30 minutes."));
  }

  @Test
  void escapesUserSuppliedHtmlAndOmitsOptionalSectionsWhenMissing() {
    RenderedEmail email = template.render(EmailContent.builder("Hello <script>alert('x')</script>")
        .greeting("Hello <Alex & Sam>,")
        .paragraphs(List.of("Welcome <strong>home</strong>."))
        .build());

    assertTrue(email.html().contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
    assertTrue(email.html().contains("Hello &lt;Alex &amp; Sam&gt;,"));
    assertTrue(email.html().contains("Welcome &lt;strong&gt;home&lt;/strong&gt;."));
    assertFalse(email.html().contains("<script>"));
    assertFalse(email.html().contains("Reset password"));
    assertFalse(email.text().contains("null"));
  }
}
