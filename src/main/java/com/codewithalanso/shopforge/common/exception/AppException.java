package com.codewithalanso.shopforge.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for custom business logic failures, carrying an HTTP status.
 */
public class AppException extends RuntimeException {
    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
