package com.gharse.catalog;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.catalog.dto.ProductRequest;
import com.gharse.catalog.dto.ProductResponse;
import com.gharse.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created.", service.create(user, req)));
    }

    @PutMapping("/products/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ProductResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long productId,
            @Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok("Product updated.", service.update(user, productId, req));
    }

    @DeleteMapping("/products/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Void> deactivate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long productId) {
        service.deactivate(user, productId);
        return ApiResponse.ok("Product removed.", null);
    }

    @GetMapping("/seller/me/products")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<List<ProductResponse>> myProducts(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.listMine(user));
    }
}
