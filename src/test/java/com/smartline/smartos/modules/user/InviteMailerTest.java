package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.mail.BrandedEmailSender;
import com.smartline.smartos.common.mail.BrandedEmailTemplate;
import com.smartline.smartos.common.mail.EmailBrand;
import com.smartline.smartos.common.mail.EmailContent;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.security.Role;
import org.junit.jupiter.api.Test;

class InviteMailerTest {

  @Test
  void sendsInvitationContentToTheEnteredAddress() {
    BrandedEmailSender sender = mock(BrandedEmailSender.class);
    when(sender.send(anyString(), anyString(), any(EmailContent.class))).thenReturn(true);
    InviteMailer mailer = mailer(sender);

    assertTrue(mailer.send(organization(), "invitee@example.com", "single-use-token",
        Role.EDITOR, 7));

    var contentCaptor = org.mockito.ArgumentCaptor.forClass(EmailContent.class);
    verify(sender).send(eq("invitee@example.com"), eq("You've been invited to Acme"),
        contentCaptor.capture());
    BrandedEmailTemplate.RenderedEmail rendered =
        new BrandedEmailTemplate(EmailBrand.defaults()).render(contentCaptor.getValue());
    assertTrue(rendered.text().contains(
        "https://app.smartos.example/invite/accept?token=single-use-token"));
    assertTrue(rendered.html().contains("Acme"));
    assertTrue(rendered.html().contains("Accept invitation"));
    assertTrue(rendered.text().contains("editor"));
    assertTrue(rendered.text().contains("7 days"));
  }

  @Test
  void returnsFalseWhenSharedSenderReportsDeliveryFailure() {
    BrandedEmailSender sender = mock(BrandedEmailSender.class);
    when(sender.send(anyString(), anyString(), any(EmailContent.class))).thenReturn(false);
    InviteMailer mailer = mailer(sender);

    assertFalse(mailer.send(organization(), "invitee@example.com", "single-use-token",
        Role.VIEWER, 7));
  }

  @Test
  void skipsBlankRecipientAddress() {
    BrandedEmailSender sender = mock(BrandedEmailSender.class);
    InviteMailer mailer = mailer(sender);

    assertFalse(mailer.send(organization(), " ", "single-use-token", Role.VIEWER, 7));
    verify(sender, never()).send(anyString(), anyString(), any(EmailContent.class));
  }

  private static InviteMailer mailer(BrandedEmailSender sender) {
    return new InviteMailer(sender, EmailBrand.defaults(), "https://app.smartos.example");
  }

  private static Organization organization() {
    Organization org = new Organization();
    org.setId(java.util.UUID.randomUUID());
    org.setName("Acme");
    org.setSlug("acme");
    org.setPlan(Organization.Plan.FREE);
    return org;
  }
}
