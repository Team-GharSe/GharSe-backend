package com.gharse.seller.dto;

import com.gharse.seller.SellerProfile;

import java.math.BigDecimal;

public record SellerProfileResponse(
        Long id,
        String businessName,
        BigDecimal latitude,
        BigDecimal longitude) {

    public static SellerProfileResponse from(SellerProfile profile) {
        return new SellerProfileResponse(
                profile.getId(),
                profile.getBusinessName(),
                profile.getLatitude(),
                profile.getLongitude());
    }
}