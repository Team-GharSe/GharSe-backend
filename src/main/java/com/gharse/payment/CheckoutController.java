package com.gharse.payment;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/checkout")
public class CheckoutController {

    private final PaymentService paymentService;

    public CheckoutController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public ApiResponse<CheckoutInitiateResponse> initiate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CheckoutInitiateRequest request) {
        return ApiResponse.ok(paymentService.initiate(
                user.id(), request.fulfilmentMode(), request.deliveryAddress()));
    }
}