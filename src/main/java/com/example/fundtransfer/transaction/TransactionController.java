package com.example.fundtransfer.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Transaction transfer(@Valid @RequestBody TransferRequestBody body) {
        TransactionService.TransferRequest req = new TransactionService.TransferRequest(
                body.fromAccount(),
                body.toAccount(),
                body.amount(),
                body.currency() == null ? "INR" : body.currency(),
                body.idempotencyKey()
        );
        return transactionService.startTransfer(req);
    }

    @GetMapping("/{transactionRef}")
    public Transaction getStatus(@PathVariable String transactionRef) {
        return transactionService.getByRef(transactionRef)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionRef));
    }

    @PostMapping("/{transactionRef}/reverse")
    public Transaction reverse(@PathVariable String transactionRef) {
        return transactionService.reverse(transactionRef);
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


