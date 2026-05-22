package com.feedsystem.user.controller;

import com.feedsystem.common.dto.ApiResponse;
import com.feedsystem.common.dto.UserDTO;
import com.feedsystem.user.dto.AuthResponse;
import com.feedsystem.user.dto.LoginRequest;
import com.feedsystem.user.dto.RegisterRequest;
import com.feedsystem.user.dto.UpdateProfileRequest;
import com.feedsystem.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User API", description = "User registration, login, profile, and follow")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and get JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.login(request)));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user profile")
    public ResponseEntity<ApiResponse<UserDTO>> getProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user profile (bio, avatar)")
    public ResponseEntity<ApiResponse<UserDTO>> updateProfile(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long currentUserId,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (!userId.equals(currentUserId)) {
            return ResponseEntity.status(403).body(ApiResponse.error(403, "Cannot edit another user's profile"));
        }
        return ResponseEntity.ok(ApiResponse.success(userService.updateProfile(userId, request)));
    }

    @PostMapping("/{userId}/follow")
    @Operation(summary = "Follow a user")
    public ResponseEntity<ApiResponse<Void>> follow(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long currentUserId) {
        userService.follow(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{userId}/follow")
    @Operation(summary = "Unfollow a user")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long currentUserId) {
        userService.unfollow(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{userId}/followers")
    @Operation(summary = "Get user's followers")
    public ResponseEntity<ApiResponse<Page<UserDTO>>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
            userService.getFollowers(userId, PageRequest.of(page, size))));
    }

    @GetMapping("/{userId}/following")
    @Operation(summary = "Get users that userId is following")
    public ResponseEntity<ApiResponse<Page<UserDTO>>> getFollowing(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
            userService.getFollowing(userId, PageRequest.of(page, size))));
    }
}
