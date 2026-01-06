package com.example.fundtransfer.transaction;

import com.example.fundtransfer.account.AccountService;
import com.example.fundtransfer.common.InsufficientBalanceException;
import com.example.fundtransfer.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final NotificationService notificationService;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountService accountService,
                              NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.notificationService = notificationService;
    }

    public Transaction startTransfer(TransferRequest request) {
        // Idempotency: return existing transaction if same idempotency_key
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Transaction tx = new Transaction();
        tx.setTransactionRef(UUID.randomUUID().toString());
        tx.setFromAccount(request.fromAccount());
        tx.setToAccount(request.toAccount());
        tx.setAmount(request.amount());
        tx.setCurrency(request.currency());
        tx.setStatus(Transaction.Status.INITIATED);
        tx.setIdempotencyKey(request.idempotencyKey());
        transactionRepository.save(tx);

        // Choreography-style saga: publish domain events by calling local methods
        try {
            processSaga(tx);
        } catch (Exception ex) {
            log.error("Transfer saga failed for {}", tx.getTransactionRef(), ex);
        }

        return tx;
    }

    /**
     * Saga: reserve funds -> credit destination.
     * Compensation: on failure, unlock/refund.
     */
    @CircuitBreaker(name = "accountService")
    @Retry(name = "accountService")
    @Transactional
    protected void processSaga(Transaction tx) {
        tx.setStatus(Transaction.Status.PROCESSING);
        transactionRepository.save(tx);

        BigDecimal amount = tx.getAmount();
        try {
            // Step 1: reserve funds (debit with lock)
            accountService.lockFunds(tx.getFromAccount(), amount, tx.getTransactionRef(),
                    OffsetDateTime.now().plusMinutes(5));

            // Step 2: credit destination
            accountService.credit(tx.getToAccount(), amount);

            // Step 3: mark success
            tx.setStatus(Transaction.Status.SUCCESS);
            tx.setUpdatedAt(OffsetDateTime.now());
            transactionRepository.save(tx);

            // Notify (fire-and-forget)
            notificationService.sendTransactionCompleted(
                    "demo@example.com",
                    tx.getTransactionRef(),
                    tx.getFromAccount(),
                    tx.getToAccount(),
                    tx.getAmount().toPlainString()
            );
        } catch (InsufficientBalanceException ex) {
            tx.setStatus(Transaction.Status.FAILED);
            tx.setFailureReason(ex.getMessage());
            tx.setUpdatedAt(OffsetDateTime.now());
            transactionRepository.save(tx);
            throw ex;
        } catch (Exception ex) {
            // Compensation: refund source account
            compensate(tx, amount, ex);
            throw ex;
        }
    }

    @Transactional
    protected void compensate(Transaction tx, BigDecimal amount, Exception cause) {
        log.warn("Compensating transaction {} due to {}", tx.getTransactionRef(), cause.toString());
        try {
            accountService.unlockFunds(tx.getFromAccount(), amount);
        } catch (Exception unlockEx) {
            log.error("Failed to compensate transaction {}", tx.getTransactionRef(), unlockEx);
        }
        tx.setStatus(Transaction.Status.FAILED);
        tx.setFailureReason("Compensated due to error: " + cause.getMessage());
        tx.setUpdatedAt(OffsetDateTime.now());
        transactionRepository.save(tx);
    }

    public Optional<Transaction> getByRef(String transactionRef) {
        return transactionRepository.findByTransactionRef(transactionRef);
    }

    @Transactional
    public Transaction reverse(String transactionRef) {
        Transaction tx = transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionRef));

        if (tx.getStatus() != Transaction.Status.SUCCESS) {
            throw new IllegalStateException("Only successful transactions can be reversed");
        }

        BigDecimal amount = tx.getAmount();

        // Reverse: debit destination, credit source
        accountService.lockFunds(tx.getToAccount(), amount, "REV-" + tx.getTransactionRef(),
                OffsetDateTime.now().plusMinutes(5));
        accountService.credit(tx.getFromAccount(), amount);
        accountService.unlockFunds(tx.getToAccount(), amount);

        tx.setStatus(Transaction.Status.FAILED);
        tx.setFailureReason("Reversed by user");
        tx.setUpdatedAt(OffsetDateTime.now());
        transactionRepository.save(tx);
        return tx;
    }

    public record TransferRequest(
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {
    }
}


