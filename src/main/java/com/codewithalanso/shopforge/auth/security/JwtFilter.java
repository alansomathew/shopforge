package com.codewithalanso.shopforge.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that intercepts requests, checks for a Bearer JWT, validates it, and sets the Security Context.
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userId;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userId = jwtUtil.extractSubject(jwt);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Load user details by UUID user ID extracted from JWT subject
                UserDetails userDetails = this.userDetailsService.loadUserById(userId);

                if (jwtUtil.validateToken(jwt, userDetails.getUsername())) { // CustomUserDetails.getUsername() returns user ID
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token has expired: " + e.getMessage());
            sendUnauthorized(response, "Access token has expired");
            return;
        } catch (UsernameNotFoundException e) {
            logger.warn("User in JWT token not found: " + e.getMessage());
            sendUnauthorized(response, "Invalid access token");
            return;
        } catch (JwtException e) {
            logger.warn("JWT token validation failed: " + e.getMessage());
            sendUnauthorized(response, "Invalid access token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * A request that presented a Bearer token and had it rejected must come back as 401, not
     * silently fall through as an anonymous request. Before this fix, the catch blocks above
     * just logged a warning and let filterChain.doFilter(...) continue -- which meant Spring
     * Security treated the request as anonymous instead of rejected. Anonymous authentication
     * specifically fails SecurityConfig's .anyRequest().authenticated() rule (it means "any
     * *non-anonymous* authenticated user", not just "not unauthenticated"), which Spring reports
     * as an AccessDeniedException -> 403 Forbidden via GlobalExceptionHandler. The frontend's
     * axios interceptor (lib/api/client.ts) only knows how to refresh-and-retry on a 401, so an
     * expired access token -- something that happens routinely, 15 minutes into any session --
     * was surfacing as a confusing, unrecoverable "Forbidden" instead of a silent token refresh.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        // Matches ApiResponse's shape by hand rather than pulling in an ObjectMapper bean --
        // Spring Boot 4.1 didn't have a plain unqualified ObjectMapper bean available for
        // constructor injection here, and this filter only ever needs to write this one fixed,
        // trusted (not user-input-derived) message, so a manually-built JSON string is simpler
        // and has one fewer dependency to wire up correctly.
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }
}
