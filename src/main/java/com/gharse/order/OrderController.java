package com.gharse.order;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.dto.ApiResponse;
import com.gharse.order.dto.OrderResponse;
import com.gharse.order.dto.UpdateOrderStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.List;

@RestController
@RequestMapping("/v1")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) { this.orders = orders; }

    @GetMapping("/orders/me")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ApiResponse<List<OrderResponse>> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(orders.myOrders(user));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ApiResponse<OrderResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(orders.getOrder(user, orderId));
    }

    @GetMapping("/seller/me/orders")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<List<OrderResponse>> sellerOrders(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "status", required = false) OrderStatus status) {
        return ApiResponse.ok(orders.sellerOrders(user, status));
    }

    @PatchMapping("/orders/{orderId}/status")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<OrderResponse> status(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.ok(orders.updateStatus(user, orderId, request.status()));
    }

    // Local handler avoids editing the team's shared GlobalExceptionHandler.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalidParameter(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST,
            "Invalid order ID or status filter. Use an integer ID and a valid uppercase status."));
    }
}
