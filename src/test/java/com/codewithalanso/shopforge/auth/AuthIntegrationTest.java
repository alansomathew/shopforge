package com.codewithalanso.shopforge.auth;

import com.codewithalanso.shopforge.auth.dto.*;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.entities.UserStatus;
import com.codewithalanso.shopforge.repositories.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    private final RestTemplate restTemplate = new RestTemplate();

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    private String testEmail;
    private String testPassword;
    private String testFirstName;
    private String testLastName;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/v1/auth";
    }

    @BeforeEach
    void setUp() {
        // Clean database before each test
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        inventoryRepository.deleteAll();
        productImageRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
        userRepository.deleteAll();

        testEmail = "integration-test-user@shopforge.com";
        testPassword = "Password123!";
        testFirstName = "Integration";
        testLastName = "Tester";
    }

    @AfterEach
    void tearDown() {
        // Clean database after each test to leave it pristine
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        inventoryRepository.deleteAll();
        productImageRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(testEmail);
        request.setPassword(testPassword);
        request.setFirstName(testFirstName);
        request.setLastName(testLastName);

        ResponseEntity<ApiResponse<Map<String, String>>> response = restTemplate.exchange(
                getBaseUrl() + "/register",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData().get("userId"));

        // Check user is saved in DB
        UUID userId = UUID.fromString(response.getBody().getData().get("userId"));
        assertTrue(userRepository.findById(userId).isPresent());
        User user = userRepository.findById(userId).get();
        assertEquals(testEmail.toLowerCase(), user.getEmail());
        assertFalse(user.isEmailVerified());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void testRegisterDuplicateEmail() {
        // Create user first
        RegisterRequest firstRequest = new RegisterRequest();
        firstRequest.setEmail(testEmail);
        firstRequest.setPassword(testPassword);
        firstRequest.setFirstName(testFirstName);
        firstRequest.setLastName(testLastName);

        restTemplate.postForEntity(getBaseUrl() + "/register", firstRequest, ApiResponse.class);

        // Attempt duplicate registration
        RegisterRequest duplicateRequest = new RegisterRequest();
        duplicateRequest.setEmail(testEmail); // Same email
        duplicateRequest.setPassword(testPassword);
        duplicateRequest.setFirstName("Another");
        duplicateRequest.setLastName("User");

        try {
            restTemplate.exchange(
                    getBaseUrl() + "/register",
                    HttpMethod.POST,
                    new HttpEntity<>(duplicateRequest),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );
            fail("Should have thrown HttpClientErrorException.BadRequest");
        } catch (HttpClientErrorException ex) {
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            // Optionally check body if needed
        }
    }

    @Test
    void testRegisterValidationError() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalid-email"); // Invalid email format
        request.setPassword("short"); // Invalid password (must be 8+ chars and contain upper, digit, special)
        request.setFirstName(""); // Blank first name

        try {
            restTemplate.exchange(
                    getBaseUrl() + "/register",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
            );
            fail("Should have thrown HttpClientErrorException.BadRequest");
        } catch (HttpClientErrorException ex) {
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    @Test
    void testLoginSuccess() {
        // Register user first
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(testEmail);
        registerRequest.setPassword(testPassword);
        registerRequest.setFirstName(testFirstName);
        registerRequest.setLastName(testLastName);
        restTemplate.postForEntity(getBaseUrl() + "/register", registerRequest, ApiResponse.class);

        // Try standard login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(testEmail);
        loginRequest.setPassword(testPassword);

        ResponseEntity<ApiResponse<TokenResponse>> response = restTemplate.exchange(
                getBaseUrl() + "/login",
                HttpMethod.POST,
                new HttpEntity<>(loginRequest),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        TokenResponse tokens = response.getBody().getData();
        assertNotNull(tokens);
        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertEquals(testEmail.toLowerCase(), tokens.getUser().getEmail());
        assertEquals(testFirstName, tokens.getUser().getFirstName());
    }

    @Test
    void testLoginWrongPassword() {
        // Register user
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(testEmail);
        registerRequest.setPassword(testPassword);
        registerRequest.setFirstName(testFirstName);
        restTemplate.postForEntity(getBaseUrl() + "/register", registerRequest, ApiResponse.class);

        // Try login with incorrect password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(testEmail);
        loginRequest.setPassword("WrongPassword123!");

        try {
            restTemplate.exchange(
                    getBaseUrl() + "/login",
                    HttpMethod.POST,
                    new HttpEntity<>(loginRequest),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );
            fail("Should have thrown HttpClientErrorException.Unauthorized");
        } catch (HttpClientErrorException ex) {
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        // Check failed attempts incremented in DB
        User user = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
        assertEquals(1, user.getFailedLoginAttempts());
    }

    @Test
    void testLoginLockoutBehavior() {
        // Register user
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(testEmail);
        registerRequest.setPassword(testPassword);
        registerRequest.setFirstName(testFirstName);
        restTemplate.postForEntity(getBaseUrl() + "/register", registerRequest, ApiResponse.class);

        // Login with wrong password 5 times
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(testEmail);
        loginRequest.setPassword("WrongPassword123!");

        for (int i = 0; i < 4; i++) {
            try {
                restTemplate.postForEntity(getBaseUrl() + "/login", loginRequest, ApiResponse.class);
            } catch (HttpClientErrorException.Unauthorized ex) {
                // expected
            }
        }

        // The 5th attempt should lock out
        try {
            restTemplate.exchange(
                    getBaseUrl() + "/login",
                    HttpMethod.POST,
                    new HttpEntity<>(loginRequest),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );
            fail("Should have locked out");
        } catch (HttpClientErrorException ex) {
            assertEquals(HttpStatus.LOCKED, ex.getStatusCode());
        }

        // Try standard login now with correct credentials, should still be locked
        LoginRequest correctRequest = new LoginRequest();
        correctRequest.setEmail(testEmail);
        correctRequest.setPassword(testPassword);

        try {
            restTemplate.exchange(
                    getBaseUrl() + "/login",
                    HttpMethod.POST,
                    new HttpEntity<>(correctRequest),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );
            fail("Should have thrown HttpClientErrorException.Locked");
        } catch (HttpClientErrorException ex) {
            assertEquals(HttpStatus.LOCKED, ex.getStatusCode());
        }
    }

    @Test
    void testTokenRefreshRotation() {
        // Register and login to get refresh token
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(testEmail);
        registerRequest.setPassword(testPassword);
        registerRequest.setFirstName(testFirstName);
        restTemplate.postForEntity(getBaseUrl() + "/register", registerRequest, ApiResponse.class);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(testEmail);
        loginRequest.setPassword(testPassword);
        
        ResponseEntity<ApiResponse<TokenResponse>> loginRes = restTemplate.exchange(
                getBaseUrl() + "/login",
                HttpMethod.POST,
                new HttpEntity<>(loginRequest),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() {}
        );
        String oldRefreshToken = loginRes.getBody().getData().getRefreshToken();

        // Perform token refresh rotation
        RefreshTokenRequest refreshReq = new RefreshTokenRequest();
        refreshReq.setRefreshToken(oldRefreshToken);

        ResponseEntity<ApiResponse<TokenResponse>> refreshRes = restTemplate.exchange(
                getBaseUrl() + "/refresh",
                HttpMethod.POST,
                new HttpEntity<>(refreshReq),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() {}
        );

        assertEquals(HttpStatus.OK, refreshRes.getStatusCode());
        assertNotNull(refreshRes.getBody());
        assertTrue(refreshRes.getBody().isSuccess());

        TokenResponse newTokens = refreshRes.getBody().getData();
        assertNotNull(newTokens.getAccessToken());
        assertNotNull(newTokens.getRefreshToken());
        assertNotEquals(oldRefreshToken, newTokens.getRefreshToken());

        // Attempting to refresh again using the OLD refresh token should trigger reuse detection and revoke all tokens
        try {
            restTemplate.exchange(
                    getBaseUrl() + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshReq),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );
            fail("Should have failed token reuse");
        } catch (HttpClientErrorException ex) {
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        // Check refresh token table is empty for user
        User user = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
        long tokenCount = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .count();
        assertEquals(0, tokenCount);
    }

    @Test
    void testLogoutSuccess() {
        // Register and login to get refresh token
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(testEmail);
        registerRequest.setPassword(testPassword);
        registerRequest.setFirstName(testFirstName);
        restTemplate.postForEntity(getBaseUrl() + "/register", registerRequest, ApiResponse.class);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(testEmail);
        loginRequest.setPassword(testPassword);
        
        ResponseEntity<ApiResponse<TokenResponse>> loginRes = restTemplate.exchange(
                getBaseUrl() + "/login",
                HttpMethod.POST,
                new HttpEntity<>(loginRequest),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() {}
        );
        String refreshToken = loginRes.getBody().getData().getRefreshToken();

        // Perform logout
        RefreshTokenRequest logoutReq = new RefreshTokenRequest();
        logoutReq.setRefreshToken(refreshToken);

        ResponseEntity<Void> logoutResponse = restTemplate.postForEntity(
                getBaseUrl() + "/logout",
                logoutReq,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, logoutResponse.getStatusCode());

        // Refresh using the logged out token should fail
        try {
            restTemplate.exchange(
                    getBaseUrl() + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(logoutReq),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );
            fail("Should have failed refresh on logged out token");
        } catch (HttpClientErrorException ex) {
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }
    }
}
