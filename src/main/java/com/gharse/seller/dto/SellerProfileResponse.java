package com.gharse.seller.dto;

import com.gharse.seller.SellerProfile;

import java.math.BigDecimal;

public record SellerProfileResponse(
        Long id,
        String businessName,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal averageRating,
        long reviewCount) {

    /** Profile without review data (used by /v1/seller/me). */
    public static SellerProfileResponse from(SellerProfile profile) {
        return of(profile, null, 0L);
    }

    /** Profile with review aggregates (used by the public seller endpoint). */
    public static SellerProfileResponse of(SellerProfile profile, BigDecimal averageRating, long reviewCount) {
        return new SellerProfileResponse(
                profile.getId(),
                profile.getBusinessName(),
                profile.getLatitude(),
                profile.getLongitude(),
                averageRating,
                reviewCount);
    }
}