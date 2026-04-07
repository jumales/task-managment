package com.demo.common.exception;

/**
 * Thrown when a business rule is violated (e.g. booked work in PLANNING phase).
 * Mapped to HTTP 422 Unprocessable Entity by {@link GlobalExceptionHandler}.
 */
public class BusinessLogicException extends RuntimeException {

    public BusinessLogicException(String message) {
        super(message);
    }
}
