package com.gharse.catalog;

import com.gharse.catalog.dto.ProductResponse;
import com.gharse.catalog.dto.ProductSearchResult;
import com.gharse.common.dto.ApiResponse;
import com.gharse.review.ReviewService;
import com.gharse.review.dto.ReviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ProductPublicController {

    private final ProductQueryService service;
    private final ReviewService reviewService;

    public ProductPublicController(ProductQueryService service, ReviewService reviewService) {
        this.service = service;
        this.reviewService = reviewService;
    }


    @GetMapping("/products/search")
    public ApiResponse<ProductQueryService.Page<ProductSearchResult>> search(
            @RequestParam(required = false) String q,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "min_price", required = false) BigDecimal minPrice,
            @RequestParam(name = "max_price", required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(name = "radius_km", required = false) Double radiusKm,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "12") int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), 50);
        return ApiResponse.ok(service.search(q, categoryId, minPrice, maxPrice,
                lat, lng, radiusKm, sort, safePage, safeSize));
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<ProductResponse> detail(@PathVariable Long productId) {
        return ApiResponse.ok(service.getById(productId));
    }

    /** Optional quantity (default 1) so the date matches a cart holding that many. */
    @GetMapping("/products/{productId}/eta")
    public ApiResponse<Map<String, Object>> eta(
            @PathVariable Long productId,
            @RequestParam(name = "quantity", defaultValue = "1") int quantity) {
        return ApiResponse.ok(service.eta(productId, quantity));
    }

    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<List<ReviewResponse>> reviews(@PathVariable Long productId) {
        return ApiResponse.ok(reviewService.listForProduct(productId));
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.ok(service.categories());
    }

    // Local handler avoids editing the team's shared GlobalExceptionHandler.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalidParameter(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, "Invalid product id or quantity."));
    }
}