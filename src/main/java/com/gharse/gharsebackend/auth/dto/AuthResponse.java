package com.gharse.gharsebackend.auth.dto;

public record AuthResponse(
        String token,
        String role,
        Long userId,
        String name) {
}