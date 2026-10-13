package com.gharse.auth;

import com.gharse.auth.dto.AuthResponse;
import com.gharse.auth.dto.LoginRequest;
import com.gharse.auth.dto.RegisterRequest;
import com.gharse.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(auth.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(auth.login(req));
    }
}
