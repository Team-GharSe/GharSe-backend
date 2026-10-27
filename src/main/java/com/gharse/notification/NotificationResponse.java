package com.gharse.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record NotificationResponse(
        @JsonProperty("notification_id") Long notificationId,
        @JsonProperty("channel") NotificationChannel channel,
        @JsonProperty("type") String type,
        @JsonProperty("content") String content,
        @JsonProperty("status") NotificationStatus status,
        @JsonProperty("sent_at") LocalDateTime sentAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getChannel(), n.getType(),
                n.getContent(), n.getStatus(), n.getSentAt());
    }
}