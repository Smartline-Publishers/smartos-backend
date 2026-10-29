package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.mail.BrandedEmailSender;
import com.smartline.smartos.common.mail.BrandedEmailTemplate;
import com.smartline.smartos.common.mail.EmailBrand;
import com.smartline.smartos.common.mail.EmailContent;
import org.junit.jupiter.api.Test;

class PasswordResetMailerTest {

  @Test
  void sendsPasswordResetContentThroughSharedBrandedSender() {
    BrandedEmailSender sender = org.mockito.Mockito.mock(BrandedEmailSender.class);
    when(sender.send(anyString(), anyString(), any(EmailContent.class))).thenReturn(true);
    PasswordResetMailer mailer = mailer(sender);
    AppUser user = userWithEmail("alex@example.com");

    assertTrue(mailer.send(user, "single-use-token"));

    var contentCaptor = org.mockito.ArgumentCaptor.forClass(EmailContent.class);
    verify(sender).send(eq("alex@example.com"), eq("Reset your SmartOS password"),
        contentCaptor.capture());
    BrandedEmailTemplate.RenderedEmail rendered =
        new BrandedEmailTemplate(EmailBrand.defaults()).render(contentCaptor.getValue());
    assertTrue(rendered.text().contains(
        "https://app.smartos.example/reset-password?token=single-use-token"));
    assertTrue(rendered.html().contains("SmartOS"));
    assertTrue(rendered.html().contains("Reset password"));
  }

  @Test
  void returnsFalseWhenSharedSenderReportsDeliveryFailure() {
    BrandedEmailSender sender = org.mockito.Mockito.mock(BrandedEmailSender.class);
    when(sender.send(anyString(), anyString(), any(EmailContent.class))).thenReturn(false);
    PasswordResetMailer mailer = mailer(sender);

    assertFalse(mailer.send(userWithEmail("alex@example.com"), "single-use-token"));
  }

  @Test
  void skipsUsersWithoutAnEmailAddress() {
    BrandedEmailSender sender = org.mockito.Mockito.mock(BrandedEmailSender.class);
    PasswordResetMailer mailer = mailer(sender);
    AppUser user = userWithEmail(" ");

    assertFalse(mailer.send(user, "single-use-token"));
    verify(sender, never()).send(anyString(), anyString(), any(EmailContent.class));
  }

  private static PasswordResetMailer mailer(BrandedEmailSender sender) {
    return new PasswordResetMailer(sender, EmailBrand.defaults(),
        "https://app.smartos.example", 30);
  }

  private static AppUser userWithEmail(String email) {
    AppUser user = new AppUser();
    user.setEmail(email);
    user.setFullName("Alex");
    return user;
  }
}
