package com.example.fundtransfer.transaction;

import com.example.fundtransfer.account.AccountService;
import com.example.fundtransfer.common.InsufficientBalanceException;
import com.example.fundtransfer.common.MessageSourceService;
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
    private final MessageSourceService messageSourceService;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountService accountService,
                              NotificationService notificationService,
                              MessageSourceService messageSourceService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.notificationService = notificationService;
        this.messageSourceService = messageSourceService;
    }

    /**
     * Initiates a fund transfer with idempotency support.
     * If the same idempotency_key is provided, returns the existing transaction.
     * 
     * @param request Transfer request with idempotency key
     * @return Transaction entity
     */
    @Transactional
    public Transaction startTransfer(TransferRequest request) {
        log.debug("Starting transfer: fromAccount={}, toAccount={}, amount={}, currency={}, idempotencyKey={}",
                request.fromAccount(), request.toAccount(), request.amount(), 
                request.currency(), request.idempotencyKey());
        
        // Idempotency: return existing transaction if same idempotency_key
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                Transaction existingTx = existing.get();
                log.info("Idempotent request detected, returning existing transaction: transactionRef={}, idempotencyKey={}, status={}", 
                        existingTx.getTransactionRef(), request.idempotencyKey(), existingTx.getStatus());
                return existingTx;
            }
            log.debug("No existing transaction found for idempotency key: {}", request.idempotencyKey());
        }

        // Validate that from and to accounts are different
        if (request.fromAccount().equals(request.toAccount())) {
            log.warn("Transfer validation failed: source and destination accounts are the same: account={}", 
                    request.fromAccount());
            throw new IllegalArgumentException(messageSourceService.getMessage("transaction.same.accounts"));
        }

        // Validate amount is positive
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Transfer validation failed: invalid amount: amount={}", request.amount());
            throw new IllegalArgumentException(messageSourceService.getMessage("transaction.invalid.amount"));
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
        log.info("Created new transaction: transactionRef={}, fromAccount={}, toAccount={}, amount={}, idempotencyKey={}", 
                tx.getTransactionRef(), tx.getFromAccount(), tx.getToAccount(), tx.getAmount(), tx.getIdempotencyKey());

        // Choreography-style saga: publish domain events by calling local methods
        try {
            processSaga(tx);
        } catch (Exception ex) {
            log.error("Transfer saga failed: transactionRef={}, fromAccount={}, toAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), tx.getToAccount(), tx.getAmount(), ex);
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
        log.debug("Processing transaction saga: transactionRef={}, fromAccount={}, toAccount={}, amount={}", 
                tx.getTransactionRef(), tx.getFromAccount(), tx.getToAccount(), tx.getAmount());
        
        tx.setStatus(Transaction.Status.PROCESSING);
        transactionRepository.save(tx);
        log.debug("Transaction status updated to PROCESSING: transactionRef={}", tx.getTransactionRef());

        BigDecimal amount = tx.getAmount();
        try {
            // Step 1: reserve funds (debit with lock)
            log.debug("Step 1: Locking funds from source account: transactionRef={}, fromAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), amount);
            accountService.lockFunds(tx.getFromAccount(), amount, tx.getTransactionRef(),
                    OffsetDateTime.now().plusMinutes(5));
            log.debug("Funds locked successfully: transactionRef={}, fromAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), amount);

            // Step 2: credit destination
            log.debug("Step 2: Crediting destination account: transactionRef={}, toAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getToAccount(), amount);
            accountService.credit(tx.getToAccount(), amount);
            log.debug("Funds credited successfully: transactionRef={}, toAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getToAccount(), amount);

            // Step 3: release lock from source account (funds already transferred, no balance restoration needed)
            log.debug("Step 3: Releasing lock from source account: transactionRef={}, fromAccount={}", 
                    tx.getTransactionRef(), tx.getFromAccount());
            accountService.releaseLock(tx.getFromAccount());
            log.debug("Lock released successfully: transactionRef={}, fromAccount={}", 
                    tx.getTransactionRef(), tx.getFromAccount());

            // Step 4: mark success
            tx.setStatus(Transaction.Status.SUCCESS);
            tx.setUpdatedAt(OffsetDateTime.now());
            transactionRepository.save(tx);
            log.info("Transaction completed successfully: transactionRef={}, fromAccount={}, toAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), tx.getToAccount(), tx.getAmount());

            // Notify (fire-and-forget)
            try {
                notificationService.sendTransactionCompleted(
                        "demo@example.com",
                        tx.getTransactionRef(),
                        tx.getFromAccount(),
                        tx.getToAccount(),
                        tx.getAmount().toPlainString()
                );
                log.debug("Notification sent for successful transaction: transactionRef={}", tx.getTransactionRef());
            } catch (Exception notifyEx) {
                log.warn("Failed to send notification for transaction: transactionRef={}", 
                        tx.getTransactionRef(), notifyEx);
                // Don't fail the transaction if notification fails
            }
        } catch (InsufficientBalanceException ex) {
            log.warn("Transaction failed due to insufficient balance: transactionRef={}, fromAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), amount);
            tx.setStatus(Transaction.Status.FAILED);
            tx.setFailureReason(ex.getMessage());
            tx.setUpdatedAt(OffsetDateTime.now());
            transactionRepository.save(tx);
            throw ex;
        } catch (Exception ex) {
            log.error("Transaction saga failed, initiating compensation: transactionRef={}, fromAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), amount, ex);
            // Compensation: refund source account
            compensate(tx, amount, ex);
            throw ex;
        }
    }

    @Transactional
    protected void compensate(Transaction tx, BigDecimal amount, Exception cause) {
        log.warn("Initiating compensation for failed transaction: transactionRef={}, fromAccount={}, amount={}, cause={}", 
                tx.getTransactionRef(), tx.getFromAccount(), amount, cause.getClass().getSimpleName());
        
        try {
            log.debug("Unlocking funds for compensation: transactionRef={}, fromAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), amount);
            accountService.unlockFunds(tx.getFromAccount(), amount);
            log.info("Compensation completed successfully: transactionRef={}, fromAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), amount);
        } catch (Exception unlockEx) {
            log.error("Failed to compensate transaction: transactionRef={}, fromAccount={}, amount={}", 
                    tx.getTransactionRef(), tx.getFromAccount(), amount, unlockEx);
        }
        
        tx.setStatus(Transaction.Status.FAILED);
        tx.setFailureReason(messageSourceService.getMessage("transaction.compensated", cause.getMessage()));
        tx.setUpdatedAt(OffsetDateTime.now());
        transactionRepository.save(tx);
        log.debug("Transaction marked as FAILED after compensation: transactionRef={}, failureReason={}", 
                tx.getTransactionRef(), tx.getFailureReason());
    }

    /**
     * Retrieves a transaction by its transaction reference (transactionId).
     * 
     * @param transactionRef Transaction reference/ID
     * @return Optional Transaction
     */
    public Optional<Transaction> getByRef(String transactionRef) {
        log.debug("Retrieving transaction by reference: transactionRef={}", transactionRef);
        Optional<Transaction> transaction = transactionRepository.findByTransactionRef(transactionRef);
        
        if (transaction.isPresent()) {
            log.debug("Transaction found: transactionRef={}, status={}, amount={}", 
                    transactionRef, transaction.get().getStatus(), transaction.get().getAmount());
        } else {
            log.debug("Transaction not found: transactionRef={}", transactionRef);
        }
        
        return transaction;
    }

    /**
     * Reverses a successful transaction.
     * This will debit the destination account and credit the source account.
     * 
     * @param transactionRef Transaction reference/ID to reverse
     * @return Reversed transaction
     * @throws IllegalArgumentException if transaction not found
     * @throws IllegalStateException if transaction cannot be reversed
     */
    @Transactional
    public Transaction reverse(String transactionRef) {
        log.info("Initiating transaction reversal: transactionRef={}", transactionRef);
        
        Transaction tx = transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for reversal: transactionRef={}", transactionRef);
                    return new IllegalArgumentException(messageSourceService.getMessage("transaction.not.found.reversal", transactionRef));
                });

        if (tx.getStatus() != Transaction.Status.SUCCESS) {
            log.warn("Cannot reverse transaction with invalid status: transactionRef={}, currentStatus={}", 
                    transactionRef, tx.getStatus());
            throw new IllegalStateException(
                    messageSourceService.getMessage("transaction.reverse.invalid.status", tx.getStatus().toString()));
        }

        log.debug("Transaction eligible for reversal: transactionRef={}, fromAccount={}, toAccount={}, amount={}", 
                transactionRef, tx.getFromAccount(), tx.getToAccount(), tx.getAmount());
        
        BigDecimal amount = tx.getAmount();

        try {
            // Reverse: debit destination account (take money back), credit source account (give money back)
            // Original: ACC1005 → ACC1004
            // Reverse:  ACC1004 → ACC1005 (debit ACC1004, credit ACC1005)
            log.debug("Step 1: Debiting destination account for reversal: transactionRef={}, toAccount={}, amount={}", 
                    transactionRef, tx.getToAccount(), amount);
            accountService.debit(tx.getToAccount(), amount);
            
            log.debug("Step 2: Crediting source account for reversal: transactionRef={}, fromAccount={}, amount={}", 
                    transactionRef, tx.getFromAccount(), amount);
            accountService.credit(tx.getFromAccount(), amount);

            // Mark reversal as successful
            tx.setStatus(Transaction.Status.SUCCESS);
            tx.setFailureReason(null); // Clear any previous failure reason
            tx.setUpdatedAt(OffsetDateTime.now());
            transactionRepository.save(tx);
            log.info("Transaction reversed successfully: transactionRef={}, fromAccount={}, toAccount={}, amount={}, status=SUCCESS", 
                    transactionRef, tx.getFromAccount(), tx.getToAccount(), amount);
        } catch (Exception ex) {
            log.error("Failed to reverse transaction: transactionRef={}, fromAccount={}, toAccount={}, amount={}", 
                    transactionRef, tx.getFromAccount(), tx.getToAccount(), amount, ex);
            tx.setFailureReason(messageSourceService.getMessage("transaction.reversal.failed", ex.getMessage()));
            tx.setUpdatedAt(OffsetDateTime.now());
            transactionRepository.save(tx);
            throw ex;
        }
        
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


