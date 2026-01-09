package com.example.transaction.client;

import com.example.transaction.common.InsufficientBalanceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Client for communicating with Account Service via REST API.
 */
@Component
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);

    private final WebClient webClient;

    public AccountServiceClient(@Value("${webclient.account-service-base-url:http://account-service:8081}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        log.info("AccountServiceClient initialized with base URL: {}", baseUrl);
    }

    public void lockFunds(String accountNumber, BigDecimal amount, String lockerId, OffsetDateTime expiry) {
        log.debug("Calling account service to lock funds: accountNumber={}, amount={}, lockerId={}", 
                accountNumber, amount, lockerId);
        
        LockRequest request = new LockRequest(amount, lockerId, 
                (int) java.time.Duration.between(OffsetDateTime.now(), expiry).toMinutes());
        
        try {
            webClient.put()
                    .uri("/accounts/{accountNumber}/lock", accountNumber)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Funds locked successfully via account service: accountNumber={}, amount={}", 
                    accountNumber, amount);
        } catch (WebClientResponseException ex) {
            log.error("Failed to lock funds via account service: accountNumber={}, status={}, message={}", 
                    accountNumber, ex.getStatusCode(), ex.getMessage());
            handleAccountServiceException(ex, "lock funds");
            throw ex;
        }
    }

    public void unlockFunds(String accountNumber, BigDecimal amount) {
        log.debug("Calling account service to unlock funds: accountNumber={}, amount={}", 
                accountNumber, amount);
        
        UnlockRequest request = new UnlockRequest(amount);
        
        try {
            webClient.put()
                    .uri("/accounts/{accountNumber}/unlock", accountNumber)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Funds unlocked successfully via account service: accountNumber={}, amount={}", 
                    accountNumber, amount);
        } catch (WebClientResponseException ex) {
            log.error("Failed to unlock funds via account service: accountNumber={}, status={}, message={}", 
                    accountNumber, ex.getStatusCode(), ex.getMessage());
            handleAccountServiceException(ex, "unlock funds");
            throw ex;
        }
    }

    public void releaseLock(String accountNumber) {
        log.debug("Calling account service to release lock: accountNumber={}", accountNumber);
        
        try {
            webClient.put()
                    .uri("/accounts/{accountNumber}/release-lock", accountNumber)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Lock released successfully via account service: accountNumber={}", accountNumber);
        } catch (WebClientResponseException ex) {
            log.error("Failed to release lock via account service: accountNumber={}, status={}, message={}", 
                    accountNumber, ex.getStatusCode(), ex.getMessage());
            handleAccountServiceException(ex, "release lock");
            throw ex;
        }
    }

    public void credit(String accountNumber, BigDecimal amount) {
        log.debug("Calling account service to credit: accountNumber={}, amount={}", accountNumber, amount);
        
        CreditRequest request = new CreditRequest(amount);
        
        try {
            webClient.put()
                    .uri("/accounts/{accountNumber}/credit", accountNumber)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Account credited successfully via account service: accountNumber={}, amount={}", 
                    accountNumber, amount);
        } catch (WebClientResponseException ex) {
            log.error("Failed to credit via account service: accountNumber={}, status={}, message={}", 
                    accountNumber, ex.getStatusCode(), ex.getMessage());
            handleAccountServiceException(ex, "credit");
            throw ex;
        }
    }

    public void debit(String accountNumber, BigDecimal amount) {
        log.debug("Calling account service to debit: accountNumber={}, amount={}", accountNumber, amount);
        
        DebitRequest request = new DebitRequest(amount);
        
        try {
            webClient.put()
                    .uri("/accounts/{accountNumber}/debit", accountNumber)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Account debited successfully via account service: accountNumber={}, amount={}", 
                    accountNumber, amount);
        } catch (WebClientResponseException ex) {
            log.error("Failed to debit via account service: accountNumber={}, status={}, message={}", 
                    accountNumber, ex.getStatusCode(), ex.getMessage());
            handleAccountServiceException(ex, "debit");
            throw ex;
        }
    }

    public BigDecimal getBalance(String accountNumber) {
        log.debug("Calling account service to get balance: accountNumber={}", accountNumber);
        
        try {
            BigDecimal balance = webClient.get()
                    .uri("/accounts/{accountNumber}/balance", accountNumber)
                    .retrieve()
                    .bodyToMono(BigDecimal.class)
                    .block();
            log.debug("Balance retrieved successfully via account service: accountNumber={}, balance={}", 
                    accountNumber, balance);
            return balance;
        } catch (WebClientResponseException ex) {
            log.error("Failed to get balance via account service: accountNumber={}, status={}, message={}", 
                    accountNumber, ex.getStatusCode(), ex.getMessage());
            handleAccountServiceException(ex, "get balance");
            throw ex;
        }
    }

    private void handleAccountServiceException(WebClientResponseException ex, String operation) {
        if (ex.getStatusCode().value() == 400) {
            String message = ex.getMessage();
            if (message != null && message.contains("INSUFFICIENT_BALANCE")) {
                throw new InsufficientBalanceException("Insufficient balance for account operation: " + operation);
            }
        }
        // Re-throw as-is for other cases, will be handled by GlobalExceptionHandler
    }

    // Request DTOs matching Account Service API
    public record LockRequest(
            java.math.BigDecimal amount,
            String lockedBy,
            Integer lockMinutes
    ) {
    }

    public record UnlockRequest(
            java.math.BigDecimal amount
    ) {
    }

    public record CreditRequest(
            java.math.BigDecimal amount
    ) {
    }

    public record DebitRequest(
            java.math.BigDecimal amount
    ) {
    }
}

