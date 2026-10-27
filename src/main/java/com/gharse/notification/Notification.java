package com.gharse.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/** One row per notification attempt. Created only by the server, never by a client request. */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false)
    private NotificationChannel channel;

    @Column(name = "type", nullable = false, updatable = false, length = 50)
    private String type;

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private NotificationStatus status;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    protected Notification() {}

    public Notification(Long userId, NotificationChannel channel, String type,
                        String content, NotificationStatus status) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.type = Objects.requireNonNull(type, "type");
        this.content = Objects.requireNonNull(content, "content");
        this.status = Objects.requireNonNull(status, "status");
        this.sentAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public NotificationChannel getChannel() { return channel; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public NotificationStatus getStatus() { return status; }
    public LocalDateTime getSentAt() { return sentAt; }
}