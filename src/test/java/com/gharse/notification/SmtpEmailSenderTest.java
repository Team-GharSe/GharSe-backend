package com.gharse.notification;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.*;

class SmtpEmailSenderTest {

    /** Real message building, no network: send() only captures the message or fails on demand. */
    private static class CapturingMailSender extends JavaMailSenderImpl {
        MimeMessage sent;
        RuntimeException failure;

        @Override
        public void send(MimeMessage mimeMessage) {
            if (failure != null) {
                throw failure;
            }
            this.sent = mimeMessage;
        }
    }

    private static SmtpEmailSender sender(CapturingMailSender mail) {
        return new SmtpEmailSender(mail, "support.gharse@gmail.com", "GharSe", "app-password");
    }

    @Test
    void buildsMessageWithSenderNameRecipientSubjectBodyAndFooter() throws Exception {
        CapturingMailSender mail = new CapturingMailSender();

        sender(mail).send("buyer@example.com", "GharSe order #12 update",
                "Your order #12 from Asha Bakes has been confirmed.");

        InternetAddress from = (InternetAddress) mail.sent.getFrom()[0];
        assertEquals("support.gharse@gmail.com", from.getAddress());
        assertEquals("GharSe", from.getPersonal());
        InternetAddress to = (InternetAddress) mail.sent.getRecipients(Message.RecipientType.TO)[0];
        assertEquals("buyer@example.com", to.getAddress());
        assertEquals("GharSe order #12 update", mail.sent.getSubject());
        String text = (String) mail.sent.getContent();
        assertTrue(text.startsWith("Your order #12 from Asha Bakes has been confirmed."));
        assertTrue(text.contains("automated message"));
    }

    @Test
    void mailFailurePropagatesSoTheCallerRecordsFailed() {
        CapturingMailSender mail = new CapturingMailSender();
        mail.failure = new MailSendException("smtp down");

        assertThrows(MailSendException.class, () -> sender(mail).send("buyer@example.com", "s", "b"));
    }

    @Test
    void blankRecipientIsRejectedBeforeSending() {
        CapturingMailSender mail = new CapturingMailSender();

        assertThrows(IllegalStateException.class, () -> sender(mail).send("", "s", "b"));
        assertNull(mail.sent);
    }

    @Test
    void blankSenderAddressFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new SmtpEmailSender(new CapturingMailSender(), "  ", "GharSe", "pw"));
    }

    @Test
    void blankPasswordFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new SmtpEmailSender(new CapturingMailSender(), "support.gharse@gmail.com", "GharSe", " "));
    }
}