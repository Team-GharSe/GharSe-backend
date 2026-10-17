package com.gharse.catalog;

import com.gharse.catalog.dto.ProductResponse;
import com.gharse.catalog.dto.ProductSearchResult;
import com.gharse.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ProductPublicController {

    private final ProductQueryService service;

    public ProductPublicController(ProductQueryService service) {
        this.service = service;
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

    @GetMapping("/products/{productId}/eta")
    public ApiResponse<Map<String, Object>> eta(@PathVariable Long productId) {
        return ApiResponse.ok(service.eta(productId));
    }

    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<List<Object>> reviews(@PathVariable Long productId) {
        return ApiResponse.ok(service.reviews(productId));
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.ok(service.categories());
    }
}