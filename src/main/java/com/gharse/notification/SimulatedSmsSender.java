package com.gharse.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Real SMS in India needs DLT registration, so SMS stays simulated: the message is only logged. */
@Component
public class SimulatedSmsSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmsSender.class);

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("[SIMULATED SMS] to={} body=\"{}\"", NotificationMasking.maskPhone(recipient), body);
    }
}