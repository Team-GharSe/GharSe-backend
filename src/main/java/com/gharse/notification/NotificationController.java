package com.gharse.notification;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public ApiResponse<List<NotificationResponse>> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(notificationService.myNotifications(user.id()));
    }
}