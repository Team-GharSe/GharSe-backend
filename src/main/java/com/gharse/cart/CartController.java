package com.gharse.cart;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartView> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(cartService.getCart(user.id()));
    }

    @PostMapping("/items")
    public ApiResponse<CartView> add(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.ok(cartService.addItem(user.id(), request.productId(), request.quantity()));
    }

    @PutMapping("/items/{productId}")
    public ApiResponse<CartView> update(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable("productId") Long productId,
                                        @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.ok(cartService.updateQuantity(user.id(), productId, request.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    public ApiResponse<CartView> remove(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable("productId") Long productId) {
        return ApiResponse.ok(cartService.removeItem(user.id(), productId));
    }

    @DeleteMapping
    public ApiResponse<CartView> clear(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(cartService.clear(user.id()));
    }
}