package com.gharse.user.dto;

public record UserProfileResponse(
        Long id,
        String name,
        String phone,
        String email,
        String addressText,
        String role) {
}