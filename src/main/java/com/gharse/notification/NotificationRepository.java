package com.gharse.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Newest first, capped so one buyer's history can't produce an unbounded response. */
    List<Notification> findTop50ByUserIdOrderBySentAtDescIdDesc(Long userId);
}