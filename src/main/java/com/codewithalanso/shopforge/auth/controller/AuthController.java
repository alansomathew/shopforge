package com.codewithalanso.shopforge.auth.controller;

import com.codewithalanso.shopforge.auth.dto.*;
import com.codewithalanso.shopforge.auth.service.AuthService;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller exposing all REST endpoints for user authentication, registration,
 * Google OAuth login, token rotation, and password recovery.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * User registration endpoint.
     * Returns 201 Created and the UUID of the newly registered user.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = authService.register(request);
        ApiResponse<Map<String, UUID>> response = ApiResponse.success(
                "User registered successfully. Please check your email to verify your account.",
                Map.of("userId", userId)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Standard email/password login endpoint.
     * Returns 200 OK along with the JWT access/refresh tokens and user details.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokenResponse = authService.login(request);
        ApiResponse<TokenResponse> response = ApiResponse.success("Login successful", tokenResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Google token exchange endpoint.
     * Receives Google ID token sent from the Next.js frontend, validates it, and logs the user in.
     */
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<TokenResponse>> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        TokenResponse tokenResponse = authService.loginWithGoogle(request);
        ApiResponse<TokenResponse> response = ApiResponse.success("Google login successful", tokenResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Token refresh/rotation endpoint.
     * Generates a new access token and rotates the refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = authService.refresh(request);
        ApiResponse<TokenResponse> response = ApiResponse.success("Token refreshed successfully", tokenResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint to invalidate the user session.
     * Returns 204 No Content upon success.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Email verification endpoint.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        ApiResponse<Void> response = ApiResponse.success("Email verified successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Initiates password recovery process by generating reset token and emailing it.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        ApiResponse<Void> response = ApiResponse.success("If the email exists, a password reset link has been sent.");
        return ResponseEntity.ok(response);
    }

    /**
     * Resets user password using reset token.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        ApiResponse<Void> response = ApiResponse.success("Password has been reset successfully.");
        return ResponseEntity.ok(response);
    }
}
