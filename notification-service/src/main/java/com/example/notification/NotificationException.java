package com.example.notification;

/**
 * Exception thrown when notification sending fails.
 */
public class NotificationException extends RuntimeException {
    
    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

