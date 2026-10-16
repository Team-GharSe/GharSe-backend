package com.gharse.user;

import com.gharse.auth.AuthService;
import com.gharse.common.exception.ConflictException;
import com.gharse.common.exception.UnauthorizedException;
import com.gharse.user.dto.UpdateUserRequest;
import com.gharse.user.dto.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final AuthService auth;

    public UserService(UserRepository users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    public UserProfileResponse me(Long userId) {
        return toResponse(load(userId));
    }

    @Transactional
    public UserProfileResponse update(Long userId, UpdateUserRequest req) {
        User user = load(userId);
        String email = blankToNull(req.email());

        boolean emailChanged = email != null && !email.equalsIgnoreCase(user.getEmail());
        if (emailChanged && users.existsByEmail(email)) {
            throw new ConflictException("Email already registered.");
        }

        user.setName(req.name().trim());
        user.setEmail(email);
        user.setAddressText(blankToNull(req.addressText()));
        return toResponse(users.save(user));
    }

    private User load(Long userId) {
        // Token is valid but the account is gone (for example, deleted by an admin).
        return users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Please log in again."));
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getPhone(),
                user.getEmail(),
                user.getAddressText(),
                auth.resolveRole(user));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
