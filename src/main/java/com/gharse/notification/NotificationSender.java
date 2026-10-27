package com.gharse.notification;

/** One implementation per channel. Throws if sending fails; the caller records FAILED. */
public interface NotificationSender {

    NotificationChannel channel();

    void send(String recipient, String subject, String body);
}