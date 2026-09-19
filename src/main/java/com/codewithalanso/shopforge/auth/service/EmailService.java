package com.codewithalanso.shopforge.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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

    /** Sent right after checkout creates the order, before payment is confirmed. */
    public void sendOrderPlacedEmail(String email, String orderNumber, BigDecimal totalAmount) {
        String subject = "Your ShopForge order " + orderNumber + " has been placed";
        String body = "Thanks for your order! Order " + orderNumber + " for INR " + totalAmount
                + " has been placed and is awaiting payment confirmation.";
        sendPlainTextEmail(email, subject, body);
    }

    /** Sent once a payment actually succeeds -- COD confirmation, Razorpay verify, or webhook capture. */
    public void sendPaymentConfirmedEmail(String email, String orderNumber, BigDecimal totalAmount) {
        String subject = "Payment confirmed for order " + orderNumber;
        String body = "We've received your payment of INR " + totalAmount + " for order " + orderNumber
                + ". Your order is now being processed.";
        sendPlainTextEmail(email, subject, body);
    }

    /**
     * Shared by the two methods above -- same dev-friendly pattern as the rest of this class:
     * print to the console for a smooth local dev loop (in addition to whatever Mailhog already
     * shows at http://localhost:8025), and never let an SMTP failure break the caller's actual
     * business operation (an order or payment already succeeded by the time this is called; a
     * dead mail server shouldn't roll that back).
     */
    private void sendPlainTextEmail(String email, String subject, String body) {
        System.out.println("====== [MAIL DEV LOGGER] ======");
        System.out.println("To: " + email);
        System.out.println("Subject: " + subject);
        System.out.println("Body: " + body);
        System.out.println("=================================");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email '{}' sent successfully to {}", subject, email);
        } catch (Exception e) {
            log.error("Failed to send email '{}' via SMTP to: {}. Error: {}", subject, email, e.getMessage());
        }
    }
}
