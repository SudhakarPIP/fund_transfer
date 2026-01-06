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
        log.info("Fetching account details for {}", accountNumber);
        return accountService.getAccount(accountNumber);
    }

    @GetMapping("/{accountNumber}/balance")
    public BigDecimal getBalance(@PathVariable String accountNumber) {
        log.info("Fetching balance for {}", accountNumber);
        return accountService.getBalance(accountNumber);
    }

    @PutMapping("/{accountNumber}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@PathVariable String accountNumber,
                     @Valid @RequestBody LockRequest request) {
        log.info("Locking funds for account {} by {} amount {} for {} minutes",
                accountNumber, request.lockedBy(), request.amount(), request.lockMinutes());

        accountService.lockFunds(accountNumber, request.amount(), request.lockedBy(),
                OffsetDateTime.now().plusMinutes(request.lockMinutes()));
    }

    @PutMapping("/{accountNumber}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable String accountNumber,
                       @Valid @RequestBody UnlockRequest request) {
        log.info("Unlocking funds for account {} amount {}", accountNumber, request.amount());
        accountService.unlockFunds(accountNumber, request.amount());
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

