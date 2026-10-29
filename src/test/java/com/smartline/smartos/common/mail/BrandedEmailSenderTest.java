package com.smartline.smartos.common.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Regression coverage for the shared outbound path. The invite and password
 * reset tests mock this class, so a failure to assemble a real MimeMessage only
 * shows up here.
 */
class BrandedEmailSenderTest {

  @Test
  void sendsMultipartMessageWithPlainTextAndHtmlAlternatives() throws Exception {
    JavaMailSenderImpl transport = mock(JavaMailSenderImpl.class);
    JavaMailSenderImpl mimeFactory = new JavaMailSenderImpl();
    when(transport.getHost()).thenReturn("smtp.example.test");
    when(transport.getPort()).thenReturn(587);
    when(transport.createMimeMessage()).thenAnswer(invocation -> mimeFactory.createMimeMessage());
    doNothing().when(transport).send(any(MimeMessage.class));
    BrandedEmailSender sender = sender(transport);

    boolean sent = sender.send("invitee@example.com", "You've been invited to Acme",
        invitationContent());

    assertTrue(sent);
    ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).send(message.capture());
    MimeMessage mime = message.getValue();
    assertEquals("invitee@example.com", mime.getAllRecipients()[0].toString());
    assertEquals("You've been invited to Acme", mime.getSubject());

    // A plain-text-only helper throws IllegalStateException when alternative
    // texts are set; the serialized message must really carry both parts.
    java.io.ByteArrayOutputStream wire = new ByteArrayOutputStream();
    mime.writeTo(wire);
    String mimeText = wire.toString(StandardCharsets.UTF_8);
    assertTrue(mimeText.contains("Content-Type: text/plain"), mimeText);
    assertTrue(mimeText.contains("Content-Type: text/html"), mimeText);
    assertTrue(mimeText.contains("multipart/"), mimeText);
  }

  @Test
  void deliversMultipartMessageOverRealSmtp() throws Exception {
    GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);
    greenMail.start();
    try {
      JavaMailSenderImpl transport = new JavaMailSenderImpl();
      transport.setHost("localhost");
      transport.setPort(greenMail.getSmtp().getPort());
      BrandedEmailSender sender = new BrandedEmailSender(provider(transport),
          new BrandedEmailTemplate(EmailBrand.defaults()), true, "no-reply@example.com", "");

      assertTrue(sender.send("invitee@example.com", "You've been invited to Acme",
          invitationContent()));

      MimeMessage[] received = greenMail.getReceivedMessages();
      assertEquals(1, received.length);
      assertEquals("invitee@example.com", received[0].getAllRecipients()[0].toString());
      List<String> contentTypes = new ArrayList<>();
      collectContentTypes(received[0].getContent(), contentTypes);
      assertTrue(contentTypes.stream().anyMatch(type -> type.startsWith("text/plain")),
          "missing plain-text alternative: " + contentTypes);
      assertTrue(contentTypes.stream().anyMatch(type -> type.startsWith("text/html")),
          "missing HTML alternative: " + contentTypes);
    } finally {
      greenMail.stop();
    }
  }

  @Test
  void reportsDeliveryFailureInsteadOfThrowingWhenMessageAssemblyBreaks() {
    JavaMailSenderImpl transport = mock(JavaMailSenderImpl.class);
    when(transport.getHost()).thenReturn("smtp.example.test");
    when(transport.getPort()).thenReturn(587);
    when(transport.createMimeMessage()).thenThrow(new IllegalStateException("boom"));
    BrandedEmailSender sender = sender(transport);

    assertFalse(sender.send("invitee@example.com", "You've been invited to Acme",
        invitationContent()));
  }

  @Test
  void reportsDeliveryFailureWhenMailIsDisabled() {
    JavaMailSenderImpl transport = mock(JavaMailSenderImpl.class);
    BrandedEmailSender sender = new BrandedEmailSender(provider(transport),
        new BrandedEmailTemplate(EmailBrand.defaults()), false, "", "");

    assertFalse(sender.send("invitee@example.com", "Subject", invitationContent()));
  }

  @Test
  void reportsDeliveryFailureWhenTheTransportIsOnlyHalfConfigured() {
    JavaMailSenderImpl transport = mock(JavaMailSenderImpl.class);
    when(transport.getHost()).thenReturn("smtp.example.test");
    when(transport.getPort()).thenReturn(587);
    when(transport.getUsername()).thenReturn("no-reply@example.com");
    when(transport.getPassword()).thenReturn("");
    BrandedEmailSender sender = sender(transport);

    assertFalse(sender.send("invitee@example.com", "Subject", invitationContent()));
    verify(transport, org.mockito.Mockito.never()).createMimeMessage();
  }

  @Test
  void isReadyRequiresEnabledTransportWithHostAndCompleteCredentials() {
    JavaMailSenderImpl transport = mock(JavaMailSenderImpl.class);
    when(transport.getHost()).thenReturn("smtp.example.test");
    when(transport.getUsername()).thenReturn("no-reply@example.com");
    when(transport.getPassword()).thenReturn("secret");

    BrandedEmailSender ready = sender(transport);
    assertTrue(ready.isReady());

    BrandedEmailSender disabled = new BrandedEmailSender(provider(transport),
        new BrandedEmailTemplate(EmailBrand.defaults()), false, "", "");
    assertFalse(disabled.isReady());
  }

  private static void collectContentTypes(Object content, List<String> contentTypes)
      throws Exception {
    if (content instanceof MimeMultipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart part = multipart.getBodyPart(i);
        contentTypes.add(part.getContentType());
        collectContentTypes(part.getContent(), contentTypes);
      }
    } else if (content instanceof MimeMessage nested) {
      collectContentTypes(nested.getContent(), contentTypes);
    }
  }

  private static BrandedEmailSender sender(JavaMailSender transport) {
    return new BrandedEmailSender(provider(transport),
        new BrandedEmailTemplate(EmailBrand.defaults()), true, "", "");
  }

  private static ObjectProvider<JavaMailSender> provider(JavaMailSender transport) {
    return new ObjectProvider<>() {
      @Override
      public JavaMailSender getObject() {
        return transport;
      }

      @Override
      public JavaMailSender getIfAvailable() {
        return transport;
      }
    };
  }

  private static EmailContent invitationContent() {
    return EmailContent.builder("You've been invited to Acme")
        .greeting("Hello there,")
        .paragraph("You have been invited to join Acme.")
        .action("Accept invitation", "https://app.example/invite/accept?token=abc")
        .build();
  }
}
