package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.mail.BrandedEmailTemplate;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class PasswordResetMailerTest {

  @Test
  void sendsBrandedHtmlAndPlainTextAlternatives() throws Exception {
    JavaMailSender sender = mock(JavaMailSender.class);
    MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("javaMailSender", sender);
    var senderProvider = beanFactory.getBeanProvider(JavaMailSender.class);
    when(sender.createMimeMessage()).thenReturn(message);

    PasswordResetMailer mailer = new PasswordResetMailer(
        senderProvider, true, "no-reply@smartos.example",
        "https://app.smartos.example", "", new BrandedEmailTemplate());
    AppUser user = new AppUser();
    user.setEmail("alex@example.com");
    user.setFullName("Alex");

    assertTrue(mailer.send(user, "single-use-token"));
    verify(sender).send(message);
    assertEquals("Reset your SmartOS password", message.getSubject());

    Multipart mixed = assertInstanceOf(Multipart.class, message.getContent());
    Multipart alternatives = assertInstanceOf(Multipart.class, mixed.getBodyPart(0).getContent());
    assertEquals("alternative", alternatives.getContentType()
        .substring(alternatives.getContentType().indexOf('/') + 1)
        .split(";")[0]);
    String text = alternatives.getBodyPart(0).getContent().toString();
    String html = alternatives.getBodyPart(1).getContent().toString();
    assertTrue(text.contains("https://app.smartos.example/reset-password?token=single-use-token"));
    assertTrue(html.contains("SmartOS"));
    assertTrue(html.contains("Reset password"));
  }

  @Test
  void handlesSmtpAuthenticationFailureWithoutExposingProviderDetails() {
    JavaMailSender sender = mock(JavaMailSender.class);
    MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("javaMailSender", sender);
    var senderProvider = beanFactory.getBeanProvider(JavaMailSender.class);
    when(sender.createMimeMessage()).thenReturn(message);
    doThrow(new MailAuthenticationException("secret provider response"))
        .when(sender).send(message);
    PasswordResetMailer mailer = new PasswordResetMailer(
        senderProvider, true, "no-reply@smartos.example",
        "https://app.smartos.example", "", new BrandedEmailTemplate());
    AppUser user = new AppUser();
    user.setEmail("alex@example.com");
    user.setFullName("Alex");

    assertFalse(mailer.send(user, "single-use-token"));
  }

  @Test
  void identifiesAuthenticationFailureWrappedBySmtpSendException() {
    JavaMailSender sender = mock(JavaMailSender.class);
    MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("javaMailSender", sender);
    var senderProvider = beanFactory.getBeanProvider(JavaMailSender.class);
    when(sender.createMimeMessage()).thenReturn(message);
    doThrow(new MailSendException("SMTP failed",
        new AuthenticationFailedException("secret provider response")))
        .when(sender).send(message);
    PasswordResetMailer mailer = new PasswordResetMailer(
        senderProvider, true, "no-reply@smartos.example",
        "https://app.smartos.example", "", new BrandedEmailTemplate());
    AppUser user = new AppUser();
    user.setEmail("alex@example.com");
    user.setFullName("Alex");

    assertFalse(mailer.send(user, "single-use-token"));
  }
}
