package com.example.notification.common;

import java.time.OffsetDateTime;

/**
 * Standardized error response format for Notification Service.
 */
public class ErrorResponse {

    private OffsetDateTime timestamp = OffsetDateTime.now();
    private int status;
    private String error;
    private String errorCode; // Alias for error field for backward compatibility
    private String message;
    private String path;

    public ErrorResponse(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.errorCode = error; // Set errorCode to same value as error
        this.message = message;
        this.path = path;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }
}

