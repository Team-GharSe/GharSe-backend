package com.gharse.auth;

import com.gharse.auth.dto.AuthResponse;
import com.gharse.auth.dto.LoginRequest;
import com.gharse.auth.dto.RegisterRequest;
import com.gharse.common.exception.ConflictException;
import com.gharse.common.exception.UnauthorizedException;
import com.gharse.seller.SellerProfileRepository;
import com.gharse.user.User;
import com.gharse.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final SellerProfileRepository sellers;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthService(UserRepository users, SellerProfileRepository sellers,
                       PasswordEncoder encoder, JwtUtil jwt) {
        this.users = users;
        this.sellers = sellers;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthResponse register(RegisterRequest req) {
        if (users.existsByPhone(req.phone())) {
            throw new ConflictException("Phone number already registered.");
        }
        if (req.email() != null && !req.email().isBlank() && users.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered.");
        }
        User user = new User();
        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email() == null || req.email().isBlank() ? null : req.email());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole("USER");
        users.save(user);
        return issue(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailOrPhone(req.identifier(), req.identifier())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials."));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials.");
        }
        return issue(user);
    }

    // Role is derived from the database, never from what the client claims.
    private AuthResponse issue(User user) {
        String role;
        if ("ADMIN".equals(user.getRole())) {
            role = "ADMIN";
        } else if (sellers.existsByUserId(user.getId())) {
            role = "SELLER";
        } else {
            role = "BUYER";
        }
        String token = jwt.generate(user.getId(), role);
        return new AuthResponse(token, role, user.getId(), user.getName());
    }
}
