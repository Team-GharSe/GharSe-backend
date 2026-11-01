package com.gharse.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/** Sends real email through Gmail SMTP. Active only when app.notifications.email.enabled=true. */
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "true")
public class SmtpEmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final String FOOTER = "\n\n--\nThis is an automated message from GharSe about your order.";

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${app.notifications.email.from:}") String from,
                           @Value("${app.notifications.email.from-name:GharSe}") String fromName,
                           @Value("${spring.mail.password:}") String password) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(
                    "Email is enabled but GMAIL_USERNAME (app.notifications.email.from) is not set.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Email is enabled but GMAIL_APP_PASSWORD (spring.mail.password) is not set.");
        }
        this.mailSender = mailSender;
        this.from = from.trim();
        this.fromName = fromName;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    /** Throws on any failure (bad address, SMTP error); the caller records the notification as FAILED. */
    @Override
    public void send(String recipient, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body + FOOTER, false);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Could not build the email message", e);
        }
        mailSender.send(message);
        log.info("[EMAIL SENT] to={} subject=\"{}\"", NotificationMasking.maskEmail(recipient), subject);
    }
}