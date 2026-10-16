package com.gharse.seller;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.auth.dto.AuthResponse;
import com.gharse.common.dto.ApiResponse;
import com.gharse.seller.dto.SellerProfileRequest;
import com.gharse.seller.dto.SellerProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/seller")
public class SellerController {

    private final SellerService sellers;

    public SellerController(SellerService sellers) {
        this.sellers = sellers;
    }

    @PostMapping("/profile")
    public ApiResponse<AuthResponse> becomeSeller(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody SellerProfileRequest req) {
        return ApiResponse.ok("Seller profile created.", sellers.becomeSeller(user.id(), req));
    }

    @GetMapping("/me")
    public ApiResponse<SellerProfileResponse> myProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(sellers.myProfile(user.id()));
    }

    @GetMapping("/{sellerProfileId}")
    public ApiResponse<SellerProfileResponse> publicProfile(@PathVariable Long sellerProfileId) {
        return ApiResponse.ok(sellers.publicProfile(sellerProfileId));
    }
}
