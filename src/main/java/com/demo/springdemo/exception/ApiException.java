package com.demo.springdemo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Single business-error type; GlobalExceptionHandler turns it into an RFC 7807 problem response. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException invalidRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "link_not_found", "Short link not found");
    }

    public static ApiException expired() {
        return new ApiException(HttpStatus.GONE, "link_expired", "Short link has expired");
    }

    public static ApiException aliasTaken() {
        return new ApiException(HttpStatus.CONFLICT, "alias_taken", "customAlias is already in use");
    }

    public static ApiException codeGenerationFailed() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "code_generation_failed",
                "Could not allocate a unique short code, please retry");
    }
}
