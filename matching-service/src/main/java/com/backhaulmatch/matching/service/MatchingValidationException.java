package com.backhaulmatch.matching.service;

import java.util.List;

/**
 * Thrown when a shipment fails validation before matching. Carries every
 * collected error so the caller (controller or recheck loop) can report
 * the full picture. Spring Boot's default exception handler converts this
 * to a 400 Bad Request with the error list in the response body.
 */
public class MatchingValidationException extends RuntimeException {

    private final List<String> errors;

    public MatchingValidationException(List<String> errors) {
        super("Shipment validation failed: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
