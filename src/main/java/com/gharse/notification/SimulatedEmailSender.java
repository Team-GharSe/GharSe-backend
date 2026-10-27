package com.gharse.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase 1 stand-in: writes the email to the app log instead of sending it. */
@Component
public class SimulatedEmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailSender.class);

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("[SIMULATED EMAIL] to={} subject=\"{}\" body=\"{}\"",
                NotificationMasking.maskEmail(recipient), subject, body);
    }
}