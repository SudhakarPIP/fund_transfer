package com.example.fundtransfer.common;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountAlreadyLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountAlreadyLocked(AccountAlreadyLockedException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_LOCKED", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "ACCOUNT_LOCKED", ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDBException(DataIntegrityViolationException ex,
                                                           HttpServletRequest request) {
        log.warn("Data integrity violation for path {}: {}", request.getRequestURI(), ex.getMessage());
        
        // Check if it's a unique constraint violation (likely account already locked)
        String message = ex.getMessage();
        if (message != null && (message.contains("Duplicate entry") || 
                                message.contains("unique constraint") || 
                                message.contains("UNIQUE constraint") ||
                                message.contains("account_locks") ||
                                message.contains("account_number"))) {
            return build(HttpStatus.CONFLICT,
                         "ACCOUNT_ALREADY_LOCKED",
                         "Account is already locked. Please unlock it first or wait for expiry.",
                         request);
        }
        
        return build(HttpStatus.CONFLICT,
                     "DATA_INTEGRITY_VIOLATION",
                     "Data integrity violation: " + (message != null ? message : "Unknown constraint violation"),
                     request);
    }

    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Concurrent update detected, please retry", request);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTxSystem(TransactionSystemException ex,
                                                        HttpServletRequest request) {
        log.warn("Transaction system exception for path {}: {}", request.getRequestURI(), ex.getMessage());
        
        // Extract root cause to provide better error message
        Throwable rootCause = ex.getRootCause();
        if (rootCause != null) {
            String rootMessage = rootCause.getMessage();
            if (rootMessage != null && (rootMessage.contains("Duplicate entry") || 
                                       rootMessage.contains("unique constraint") || 
                                       rootMessage.contains("UNIQUE constraint") ||
                                       rootMessage.contains("account_locks") ||
                                       rootMessage.contains("account_number"))) {
                return build(HttpStatus.CONFLICT,
                             "ACCOUNT_ALREADY_LOCKED",
                             "Account is already locked. Please unlock it first or wait for expiry.",
                             request);
            }
        }
        
        // Often wraps constraint violations on commit
        return build(HttpStatus.CONFLICT, 
                     "DATA_INTEGRITY_VIOLATION", 
                     "Transaction failed: " + (rootCause != null ? rootCause.getMessage() : ex.getMessage()),
                     request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
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

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException ex,
                                                          HttpServletRequest request) {
        log.error("NullPointerException for path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, 
                     "INTERNAL_ERROR", 
                     "An internal error occurred. Please check account initialization.",
                     request);
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


