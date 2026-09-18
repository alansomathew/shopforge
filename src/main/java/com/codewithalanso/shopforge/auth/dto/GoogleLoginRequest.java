package com.codewithalanso.shopforge.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO representing a Google OAuth2 token exchange request from Next.js.
 */
@Data
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID Token (credential) is required")
    private String idToken;
}
