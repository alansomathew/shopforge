package com.codewithalanso.shopforge.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service to handle sending system notification emails (e.g. signup verification, password recovery).
 * Leverages JavaMailSender and gracefully logs SMTP delivery failures for local development convenience.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Sends an email verification link to a newly registered user.
     */
    public void sendVerificationEmail(String email, String token) {
        String verificationUrl = "http://localhost:3000/auth/verify-email?token=" + token;
        String subject = "Verify your ShopForge account";
        String body = "Please verify your account by clicking the following link: " + verificationUrl;

        // Print to console to ensure smooth development experience without configuring active SMTP
        System.out.println("====== [MAIL DEV LOGGER] ======");
        System.out.println("To: " + email);
        System.out.println("Subject: " + subject);
        System.out.println("Link: " + verificationUrl);
        System.out.println("=================================");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Verification email sent successfully to {}", email);
        } catch (Exception e) {
            log.error("Failed to send verification email via SMTP to: {}. Error: {}", email, e.getMessage());
        }
    }

    /**
     * Sends a password reset link to a user.
     */
    public void sendPasswordResetEmail(String email, String token) {
        String resetUrl = "http://localhost:3000/auth/reset-password?token=" + token;
        String subject = "Reset your ShopForge password";
        String body = "You requested a password reset. Please click this link to reset it: " + resetUrl;

        System.out.println("====== [MAIL DEV LOGGER] ======");
        System.out.println("To: " + email);
        System.out.println("Subject: " + subject);
        System.out.println("Link: " + resetUrl);
        System.out.println("=================================");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Password reset email sent successfully to {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email via SMTP to: {}. Error: {}", email, e.getMessage());
        }
    }
}
