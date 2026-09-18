package com.codewithalanso.shopforge.auth.service;

import com.codewithalanso.shopforge.auth.dto.*;
import com.codewithalanso.shopforge.auth.security.JwtUtil;
import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.entities.*;
import com.codewithalanso.shopforge.repositories.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class implementing authentication business logic including credentials validation,
 * token generation, token rotation, Next.js Google SSO token exchange, and password recovery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthProviderRepository oauthProviderRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Registers a new user with standard credentials and sends a verification email.
     */
    @Transactional
    public UUID register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new AppException("Email is already registered", HttpStatus.BAD_REQUEST);
        }

        // Create new User entity
        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName() != null ? request.getLastName().trim() : null)
                .emailVerified(false)
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts((short) 0)
                .roles(new HashSet<>())
                .build();

        // Assign default CUSTOMER role
        UserRole defaultRole = UserRole.builder()
                .user(user)
                .role(Role.CUSTOMER)
                .build();
        user.getRoles().add(defaultRole);

        User savedUser = userRepository.save(user);

        // Generate a stateless 24-hour verification token using JwtUtil signing key
        String verificationToken = jwtUtil.generateVerificationToken(savedUser.getId(), savedUser.getEmail());

        // Send verification email
        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);

        return savedUser.getId();
    }

    /**
     * Validates credentials and logs in the user, returning JWT access and refresh tokens.
     */
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new AppException("Invalid email or password", HttpStatus.UNAUTHORIZED));

        // Check lockout status
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(Instant.now())) {
            throw new AppException("Account temporarily locked due to excessive failed attempts. Please try again later.", HttpStatus.LOCKED);
        }

        // Validate password
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new AppException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        // Validate user account status
        if (user.getStatus() == UserStatus.BANNED) {
            throw new AppException("Your account has been banned", HttpStatus.FORBIDDEN);
        }
        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.DELETED) {
            throw new AppException("This account has been deleted", HttpStatus.GONE);
        }

        // Reset login parameters upon success
        user.setFailedLoginAttempts((short) 0);
        user.setLockoutUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateTokensAndBuildResponse(user);
    }

    /**
     * Exchanges a Google ID Token (posted from Next.js) to authenticate a user,
     * registering them automatically if they do not exist.
     */
    @Transactional
    public TokenResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenInfo googleInfo = verifyGoogleIdToken(request.getIdToken());
        
        // Find or create OAuth provider entry
        Optional<OAuthProvider> existingProvider = oauthProviderRepository
                .findByProviderAndProviderUserId("google", googleInfo.getSub());

        User user;
        if (existingProvider.isPresent()) {
            user = existingProvider.get().getUser();
            if (user.getStatus() == UserStatus.BANNED) {
                throw new AppException("Your account has been banned", HttpStatus.FORBIDDEN);
            }
        } else {
            // Check if user exists with the same email address
            Optional<User> existingUser = userRepository.findByEmailIgnoreCase(googleInfo.getEmail());
            if (existingUser.isPresent()) {
                user = existingUser.get();
                if (user.getStatus() == UserStatus.BANNED) {
                    throw new AppException("Your account has been banned", HttpStatus.FORBIDDEN);
                }
                
                // Link Google OAuth account to the existing user
                OAuthProvider newProvider = OAuthProvider.builder()
                        .user(user)
                        .provider("google")
                        .providerUserId(googleInfo.getSub())
                        .build();
                oauthProviderRepository.save(newProvider);
            } else {
                // Auto-register new user
                user = User.builder()
                        .email(googleInfo.getEmail().toLowerCase().trim())
                        .emailVerified(true) // Verified through Google authentication
                        .firstName(googleInfo.getGiven_name() != null ? googleInfo.getGiven_name() : googleInfo.getName())
                        .lastName(googleInfo.getFamily_name())
                        .avatarUrl(googleInfo.getPicture())
                        .status(UserStatus.ACTIVE)
                        .failedLoginAttempts((short) 0)
                        .roles(new HashSet<>())
                        .build();

                UserRole defaultRole = UserRole.builder()
                        .user(user)
                        .role(Role.CUSTOMER)
                        .build();
                user.getRoles().add(defaultRole);

                User savedUser = userRepository.save(user);

                OAuthProvider newProvider = OAuthProvider.builder()
                        .user(savedUser)
                        .provider("google")
                        .providerUserId(googleInfo.getSub())
                        .build();
                oauthProviderRepository.save(newProvider);
                
                user = savedUser;
            }
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateTokensAndBuildResponse(user);
    }

    /**
     * Rotates access and refresh tokens, invalidating the old refresh token.
     * Implements token reuse detection to prevent replay attacks.
     */
    @Transactional(noRollbackFor = AppException.class)
    public TokenResponse refresh(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        String tokenHash = hashSha256(rawToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED));

        if (refreshToken.isRevoked()) {
            // Revoked token reuse detected: revoke all active tokens for this user for security
            refreshTokenRepository.deleteByUser(refreshToken.getUser());
            log.warn("Detected reuse of revoked refresh token! Revoking all refresh tokens for user ID {}", refreshToken.getUser().getId());
            throw new AppException("Token reuse detected. Session terminated.", HttpStatus.UNAUTHORIZED);
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new AppException("Refresh token expired. Please login again.", HttpStatus.UNAUTHORIZED);
        }

        // Revoke the current token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Generate new token pair
        return generateTokensAndBuildResponse(refreshToken.getUser());
    }

    /**
     * Logs out the user by revoking their active refresh token.
     */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenHash = hashSha256(request.getRefreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    /**
     * Verifies the email using a stateless verification token.
     */
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        try {
            Claims claims = jwtUtil.extractAllClaims(request.getToken());
            String purpose = claims.get("purpose", String.class);
            if (!"email-verification".equals(purpose)) {
                throw new AppException("Invalid verification token purpose", HttpStatus.BAD_REQUEST);
            }
            
            String userIdStr = claims.getSubject();
            UUID userId = UUID.fromString(userIdStr);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

            if (user.isEmailVerified()) {
                return; // Already verified
            }

            user.setEmailVerified(true);
            userRepository.save(user);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException("Invalid or expired verification token", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Requests a password reset link to be sent via email.
     * Prevents account enumeration by always returning success.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(request.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // Delete existing reset tokens if any
            passwordResetTokenRepository.deleteByUser(user);

            // Generate UUID reset token
            String rawToken = UUID.randomUUID().toString();
            String hash = hashSha256(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(hash)
                    .expiresAt(Instant.now().plusSeconds(60 * 60)) // Expires in 1 hour
                    .build();

            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        }
    }

    /**
     * Resets password using the validated recovery token.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = hashSha256(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AppException("Invalid or expired password reset token", HttpStatus.BAD_REQUEST));

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new AppException("Reset token has expired", HttpStatus.BAD_REQUEST);
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Delete used token
        passwordResetTokenRepository.delete(resetToken);

        // Terminate other active sessions for security
        refreshTokenRepository.deleteByUser(user);
    }

    // Helper method to increment failed login attempts and handle account lockouts
    private void handleFailedLogin(User user) {
        short attempts = (short) (user.getFailedLoginAttempts() + 1);
        user.setFailedLoginAttempts(attempts);

        if (attempts >= 5) {
            user.setLockoutUntil(Instant.now().plusSeconds(15 * 60)); // 15 mins
            userRepository.save(user);
            throw new AppException("Account locked due to 5 failed login attempts. Try again in 15 minutes.", HttpStatus.LOCKED);
        }
        userRepository.save(user);
    }

    // Generates a JWT access token and database-backed refresh token for the authenticated user
    private TokenResponse generateTokensAndBuildResponse(User user) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getRole().name())
                .collect(Collectors.toList());

        String accessToken = jwtUtil.generateAccessToken(user.getId(), roles);
        String rawRefreshToken = jwtUtil.generateRefreshToken();
        String hash = hashSha256(rawRefreshToken);

        // Store refresh token hash in database
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60)) // 7 days
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .roles(new HashSet<>(roles))
                .build();

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .user(userResponse)
                .build();
    }


    // Validates Next.js Google token info via official Google tokeninfo API endpoint
    private GoogleTokenInfo verifyGoogleIdToken(String idToken) {
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            GoogleTokenInfo info = restTemplate.getForObject(url, GoogleTokenInfo.class);
            if (info == null || info.getSub() == null) {
                throw new AppException("Invalid Google ID Token", HttpStatus.UNAUTHORIZED);
            }
            return info;
        } catch (Exception e) {
            throw new AppException("Google ID Token verification failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    // Computes SHA-256 hash of a string
    private String hashSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    // Internal class mapping Google Tokeninfo payload fields
    @lombok.Data
    private static class GoogleTokenInfo {
        private String sub;
        private String email;
        private String email_verified;
        private String name;
        private String given_name;
        private String family_name;
        private String picture;
    }
}
