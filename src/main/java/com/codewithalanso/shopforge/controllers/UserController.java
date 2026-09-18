package com.codewithalanso.shopforge.controllers;

import com.codewithalanso.shopforge.auth.dto.AddressRequest;
import com.codewithalanso.shopforge.auth.dto.ChangePasswordRequest;
import com.codewithalanso.shopforge.auth.dto.UpdateProfileRequest;
import com.codewithalanso.shopforge.auth.dto.UserResponse;
import com.codewithalanso.shopforge.auth.security.CustomUserDetails;
import com.codewithalanso.shopforge.auth.service.UserService;
import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.entities.Address;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller exposing user profile, password change, and address management APIs.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get all users in the system. Restricted to Admin role.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    /**
     * Fetch current authenticated user's profile details.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        
        var user = userDetails.getUser();
        var roles = user.getRoles().stream()
                .map(r -> r.getRole().name())
                .collect(Collectors.toSet());

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .roles(roles)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Fetch current user profile successful", userResponse));
    }

    /**
     * Update current authenticated user's profile details.
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        userService.updateProfile(userDetails.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully"));
    }

    /**
     * Change current authenticated user's password.
     */
    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        userService.changePassword(userDetails.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    /**
     * Fetch list of saved addresses for current user.
     */
    @GetMapping("/me/addresses")
    public ResponseEntity<ApiResponse<List<Address>>> getAddresses(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        List<Address> addresses = userService.getAddresses(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Addresses fetched successfully", addresses));
    }

    /**
     * Create a new address for current user.
     */
    @PostMapping("/me/addresses")
    public ResponseEntity<ApiResponse<Address>> createAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddressRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        Address address = userService.createAddress(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Address created successfully", address));
    }

    /**
     * Update an existing address for current user.
     */
    @PutMapping("/me/addresses/{id}")
    public ResponseEntity<ApiResponse<Address>> updateAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AddressRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        Address address = userService.updateAddress(userDetails.getUser().getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully", address));
    }

    /**
     * Delete an address for current user.
     */
    @DeleteMapping("/me/addresses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        userService.deleteAddress(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully"));
    }
}
