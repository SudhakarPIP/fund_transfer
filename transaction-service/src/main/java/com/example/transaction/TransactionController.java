package com.example.transaction;

import com.example.transaction.common.MessageSourceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;
    private final MessageSourceService messageSourceService;

    public TransactionController(TransactionService transactionService,
                                 MessageSourceService messageSourceService) {
        this.transactionService = transactionService;
        this.messageSourceService = messageSourceService;
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Transaction transfer(@Valid @RequestBody TransferRequestBody body) {
        String currency = body.currency() == null ? "INR" : body.currency();
        log.info("Received transfer request: fromAccount={}, toAccount={}, amount={}, currency={}, idempotencyKey={}",
                body.fromAccount(), body.toAccount(), body.amount(),
                currency, body.idempotencyKey());
        
        TransactionService.TransferRequest req = new TransactionService.TransferRequest(
                body.fromAccount(),
                body.toAccount(),
                body.amount(),
                currency,
                body.idempotencyKey()
        );
        
        try {
            Transaction result = transactionService.startTransfer(req);
            log.info("Transfer request processed successfully: transactionRef={}, status={}", 
                    result.getTransactionRef(), result.getStatus());
            return result;
        } catch (Exception ex) {
            log.error("Failed to process transfer request: fromAccount={}, toAccount={}, amount={}", 
                    body.fromAccount(), body.toAccount(), body.amount(), ex);
            throw ex;
        }
    }

    @GetMapping("/{transactionId}")
    public Transaction getStatus(@PathVariable String transactionId) {
        log.info("Fetching transaction status for transactionId: {}", transactionId);
        
        try {
            Transaction transaction = transactionService.getByRef(transactionId)
                    .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("transaction.not.found", transactionId)));
            log.debug("Transaction found: transactionRef={}, status={}, amount={}", 
                    transaction.getTransactionRef(), transaction.getStatus(), transaction.getAmount());
            return transaction;
        } catch (IllegalArgumentException ex) {
            log.warn("Transaction not found: transactionId={}", transactionId);
            throw ex;
        }
    }

    @PostMapping("/{transactionId}/reverse")
    public Transaction reverse(@PathVariable String transactionId) {
        log.info("Received reverse request for transactionId: {}", transactionId);
        
        try {
            Transaction result = transactionService.reverse(transactionId);
            log.info("Transaction reversed successfully: transactionRef={}, status={}", 
                    result.getTransactionRef(), result.getStatus());
            return result;
        } catch (Exception ex) {
            log.error("Failed to reverse transaction: transactionId={}", transactionId, ex);
            throw ex;
        }
    }

    public record TransferRequestBody(
            @NotBlank String fromAccount,
            @NotBlank String toAccount,
            @NotNull BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {
    }
}

