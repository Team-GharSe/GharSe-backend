package com.gharse.payment;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentStatusService statusService;

    public PaymentController(PaymentStatusService statusService) {
        this.statusService = statusService;
    }

    /** Polled by the client after Razorpay Checkout returns, because the redirect can beat the webhook. */
    @GetMapping("/{paymentId}/status")
    public ApiResponse<PaymentStatusResponse> status(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("paymentId") Long paymentId) {
        return ApiResponse.ok(statusService.getStatus(user.id(), paymentId));
    }
}