package com.gharse.catalog;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.catalog.dto.ProductResponse;
import com.gharse.common.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/products/{productId}/photos")
public class ProductPhotoController {

    private final ProductService service;

    public ProductPhotoController(ProductService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ProductResponse> add(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long productId,
            @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.ok("Photos added.", service.addPhotos(user, productId, files));
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ProductResponse> remove(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        return ApiResponse.ok("Photo removed.", service.removePhoto(user, productId, imageId));
    }
}