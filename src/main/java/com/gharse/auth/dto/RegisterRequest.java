package com.gharse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @Email String email,
        @NotBlank String password,
        @NotBlank String role) {
}
