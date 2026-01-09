package com.example.transaction.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", ex.getMessage(), request);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(WebClientResponseException ex,
                                                                  HttpServletRequest request) {
        log.warn("External service call failed: status={}, message={}", ex.getStatusCode(), ex.getMessage());
        
        // Extract error message from response body if available
        String errorMessage = ex.getMessage();
        try {
            if (ex.getResponseBodyAsString() != null && !ex.getResponseBodyAsString().isEmpty()) {
                errorMessage = ex.getResponseBodyAsString();
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        // Determine which service failed based on URL or error message
        String serviceName = "EXTERNAL_SERVICE";
        String requestUrl = ex.getRequest() != null ? ex.getRequest().getURI().toString() : "";
        if (requestUrl.contains("account-service") || requestUrl.contains("8081")) {
            serviceName = "ACCOUNT_SERVICE_ERROR";
        } else if (requestUrl.contains("notification-service") || requestUrl.contains("8083")) {
            serviceName = "NOTIFICATION_SERVICE_ERROR";
        }
        
        HttpStatus status = ex.getStatusCode().is4xxClientError() 
            ? HttpStatus.BAD_REQUEST 
            : HttpStatus.INTERNAL_SERVER_ERROR;
        
        return build(status, serviceName, errorMessage, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("Validation error");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg, request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException ex,
                                                              HttpServletRequest request) {
        log.warn("No handler found for {} {}", request.getMethod(), request.getRequestURI());
        String message = String.format("No handler found for %s %s. Please check the HTTP method and URL.", 
                request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        log.warn("Method not supported: {} for {}", request.getMethod(), request.getRequestURI());
        String message = String.format("Method %s is not supported for this endpoint. Supported methods: %s", 
                request.getMethod(), ex.getSupportedMethods() != null ? String.join(", ", ex.getSupportedMethods()) : "N/A");
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex,
                                                     HttpServletRequest request) {
        log.error("Unexpected exception for path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String errorCode, String message,
                                                HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(status.value(), errorCode, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}

