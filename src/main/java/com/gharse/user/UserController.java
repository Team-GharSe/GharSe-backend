package com.gharse.user;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.dto.ApiResponse;
import com.gharse.user.dto.UpdateUserRequest;
import com.gharse.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(users.me(user.id()));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @RequestBody UpdateUserRequest req) {
        return ApiResponse.ok("Profile updated.", users.update(user.id(), req));
    }
}
