package com.example.fundtransfer.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountNumber}")
    public AccountDto getAccount(@PathVariable String accountNumber) {
        log.info("Fetching account details for accountNumber: {}", accountNumber);
        
        try {
            AccountDto account = accountService.getAccount(accountNumber);
            log.debug("Account details retrieved successfully: accountNumber={}, balance={}, status={}", 
                    account.getAccountNumber(), account.getBalance(), account.getStatus());
            return account;
        } catch (IllegalArgumentException ex) {
            log.warn("Account not found: accountNumber={}", accountNumber);
            throw ex;
        }
    }

    @GetMapping("/{accountNumber}/balance")
    public BigDecimal getBalance(@PathVariable String accountNumber) {
        log.info("Fetching balance for accountNumber: {}", accountNumber);
        
        try {
            BigDecimal balance = accountService.getBalance(accountNumber);
            log.debug("Balance retrieved successfully: accountNumber={}, balance={}", accountNumber, balance);
            return balance;
        } catch (IllegalArgumentException ex) {
            log.warn("Account not found for balance query: accountNumber={}", accountNumber);
            throw ex;
        }
    }

    @PutMapping("/{accountNumber}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@PathVariable String accountNumber,
                     @Valid @RequestBody LockRequest request) {
        log.info("Locking funds: accountNumber={}, amount={}, lockedBy={}, lockMinutes={}",
                accountNumber, request.amount(), request.lockedBy(), request.lockMinutes());

        try {
            accountService.lockFunds(accountNumber, request.amount(), request.lockedBy(),
                    OffsetDateTime.now().plusMinutes(request.lockMinutes()));
            log.info("Funds locked successfully: accountNumber={}, amount={}, lockedBy={}", 
                    accountNumber, request.amount(), request.lockedBy());
        } catch (Exception ex) {
            log.error("Failed to lock funds: accountNumber={}, amount={}, lockedBy={}", 
                    accountNumber, request.amount(), request.lockedBy(), ex);
            throw ex;
        }
    }

    @PutMapping("/{accountNumber}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable String accountNumber,
                       @Valid @RequestBody UnlockRequest request) {
        log.info("Unlocking funds: accountNumber={}, amount={}", accountNumber, request.amount());
        
        try {
            accountService.unlockFunds(accountNumber, request.amount());
            log.info("Funds unlocked successfully: accountNumber={}, amount={}", accountNumber, request.amount());
        } catch (Exception ex) {
            log.error("Failed to unlock funds: accountNumber={}, amount={}", 
                    accountNumber, request.amount(), ex);
            throw ex;
        }
    }

    public record LockRequest(
            @NotNull @Positive BigDecimal amount,
            @NotBlank String lockedBy,
            @NotNull @Positive Integer lockMinutes
    ) {
    }

    public record UnlockRequest(
            @NotNull @Positive BigDecimal amount
    ) {
    }
}

