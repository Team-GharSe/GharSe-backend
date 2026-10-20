package com.gharse.review;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.dto.ApiResponse;
import com.gharse.review.dto.CreateReviewRequest;
import com.gharse.review.dto.ReviewResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    // Not limited to BUYER: a seller is also a buyer. Ownership is checked in the service.
    @PostMapping("/order-items/{orderItemId}/review")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable("orderItemId") Long orderItemId,
                                              @Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.ok(service.create(user, orderItemId, request));
    }
}