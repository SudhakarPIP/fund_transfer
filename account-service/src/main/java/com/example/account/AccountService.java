package com.example.account;

import com.example.account.common.AccountAlreadyLockedException;
import com.example.account.common.InsufficientBalanceException;
import com.example.account.common.MessageSourceService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final AccountLockRepository accountLockRepository;
    private final MessageSourceService messageSourceService;

    public AccountService(AccountRepository accountRepository,
                          AccountLockRepository accountLockRepository,
                          MessageSourceService messageSourceService) {
        this.accountRepository = accountRepository;
        this.accountLockRepository = accountLockRepository;
        this.messageSourceService = messageSourceService;
    }

    public AccountDto getAccount(String accountNumber) {
        log.debug("Retrieving account details: accountNumber={}", accountNumber);
        
        AccountDto account = accountRepository.findByAccountNumber(accountNumber)
                .map(AccountDto::from)
                .orElseThrow(() -> {
                    log.warn("Account not found: accountNumber={}", accountNumber);
                    return new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber));
                });
        
        log.debug("Account details retrieved: accountNumber={}, balance={}, status={}", 
                account.getAccountNumber(), account.getBalance(), account.getStatus());
        return account;
    }

    public BigDecimal getBalance(String accountNumber) {
        log.debug("Retrieving account balance: accountNumber={}", accountNumber);
        
        BigDecimal balance = accountRepository.findByAccountNumber(accountNumber)
                .map(Account::getBalance)
                .orElseThrow(() -> {
                    log.warn("Account not found for balance query: accountNumber={}", accountNumber);
                    return new IllegalArgumentException(messageSourceService.getMessage("account.not.found.balance", accountNumber));
                });
        
        log.debug("Account balance retrieved: accountNumber={}, balance={}", accountNumber, balance);
        return balance;
    }

    /**
     * Optimistic-lock protected debit reservation.
     * Cleans up expired locks before checking for existing locks.
     */
    @Transactional
    public void lockFunds(String accountNumber, BigDecimal amount, String lockerId, OffsetDateTime expiry) {
        log.info("Initiating fund lock: accountNumber={}, amount={}, lockerId={}, expiry={}", 
                accountNumber, amount, lockerId, expiry);
        
        try {
            // Clean up expired locks first
            accountLockRepository.deleteExpiredLocks(OffsetDateTime.now());
            log.debug("Cleaned up expired locks before locking account: accountNumber={}", accountNumber);
            
            executeWithOptimisticRetry(() -> {
                // Reload account to ensure we have the latest version
                Account account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));
                
                // If version is null, initialize it using a direct update query
                if (account.getVersion() == null) {
                    log.warn("Account {} has null version, initializing to 0 using direct update", accountNumber);
                    try {
                        int updated = accountRepository.initializeVersionIfNull(accountNumber);
                        if (updated > 0) {
                            // Reload the account to get the updated version
                            account = accountRepository.findByAccountNumber(accountNumber)
                                    .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));
                            log.info("Initialized version field for account {} to {}", accountNumber, account.getVersion());
                        } else {
                            // If update didn't work, try setting it manually
                            account.setVersion(0L);
                            account = accountRepository.saveAndFlush(account);
                            log.info("Initialized version field for account {} to {} using save", accountNumber, account.getVersion());
                        }
                    } catch (Exception e) {
                        log.error("Failed to initialize version for account {}: {}", accountNumber, e.getMessage(), e);
                        // Reload to see if version was set by another transaction
                        account = accountRepository.findByAccountNumber(accountNumber)
                                .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));
                        if (account.getVersion() == null) {
                            throw new IllegalStateException(messageSourceService.getMessage("account.version.initialization.failed", accountNumber));
                        }
                    }
                }

                // Check for existing active lock
                Optional<AccountLock> existingLock = accountLockRepository.findByAccountNumber(accountNumber);
                if (existingLock.isPresent()) {
                    AccountLock lock = existingLock.get();
                    // Check if lock is expired
                    if (lock.getLockExpiry().isAfter(OffsetDateTime.now())) {
                        log.warn("Account {} is already locked by {} until {}", 
                                accountNumber, lock.getLockedBy(), lock.getLockExpiry());
                        throw new AccountAlreadyLockedException(accountNumber);
                    } else {
                        // Lock expired, delete it
                        log.info("Removing expired lock for account {}", accountNumber);
                        accountLockRepository.delete(lock);
                    }
                }

                if (account.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientBalanceException(messageSourceService.getMessage("account.insufficient.balance", accountNumber));
                }

                // create lock row
                AccountLock lock = new AccountLock();
                lock.setAccountNumber(accountNumber);
                lock.setLockedBy(lockerId);
                lock.setLockTime(OffsetDateTime.now());
                lock.setLockExpiry(expiry);
                
                log.debug("Creating account lock record: accountNumber={}, lockerId={}, expiry={}", 
                        accountNumber, lockerId, expiry);
                
                try {
                    accountLockRepository.save(lock);
                    log.info("Account lock created successfully: accountNumber={}, amount={}, lockerId={}, expiry={}", 
                            accountNumber, amount, lockerId, expiry);
                } catch (DataIntegrityViolationException ex) {
                    // Handle unique constraint violation (account already locked by another transaction)
                    log.warn("Concurrent lock attempt detected: accountNumber={}, lockerId={}, error={}", 
                            accountNumber, lockerId, ex.getMessage());
                    // Re-check if lock exists (might have been created by concurrent transaction)
                    Optional<AccountLock> concurrentLock = accountLockRepository.findByAccountNumber(accountNumber);
                    if (concurrentLock.isPresent()) {
                        AccountLock existing = concurrentLock.get();
                        if (existing.getLockExpiry().isAfter(OffsetDateTime.now())) {
                            log.warn("Account is already locked by another transaction: accountNumber={}, lockedBy={}, expiry={}", 
                                    accountNumber, existing.getLockedBy(), existing.getLockExpiry());
                            throw new AccountAlreadyLockedException(accountNumber);
                        } else {
                            // Expired lock, delete and retry
                            log.info("Removing expired concurrent lock and retrying: accountNumber={}, expiredLockBy={}", 
                                    accountNumber, existing.getLockedBy());
                            accountLockRepository.delete(existing);
                            accountLockRepository.save(lock);
                            log.info("Removed expired concurrent lock and created new lock: accountNumber={}, lockerId={}", 
                                    accountNumber, lockerId);
                        }
                    } else {
                        // Unexpected: constraint violation but no lock found
                        log.error("Data integrity violation but no lock found: accountNumber={}", accountNumber);
                        throw new AccountAlreadyLockedException(accountNumber);
                    }
                }

                // reduce balance to simulate reserved funds
                // Reload account to ensure we have the latest version before updating balance
                account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));
                
                // Double-check version is initialized
                if (account.getVersion() == null) {
                    log.warn("Account {} version is null before balance update, re-initializing", accountNumber);
                    accountRepository.initializeVersionIfNull(accountNumber);
                    // Reload to get the updated version
                    account = accountRepository.findByAccountNumber(accountNumber)
                            .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));
                    if (account.getVersion() == null) {
                        throw new IllegalStateException("Failed to initialize version for account " + accountNumber);
                    }
                }
                
                BigDecimal newBalance = account.getBalance().subtract(amount);
                account.setBalance(newBalance);
                account.setUpdatedAt(OffsetDateTime.now());
                
                try {
                    accountRepository.saveAndFlush(account);
                    log.info("Fund lock completed successfully: accountNumber={}, amount={}, newBalance={}, lockerId={}", 
                            accountNumber, amount, newBalance, lockerId);
                } catch (Exception e) {
                    log.error("Failed to save account after balance update: accountNumber={}, amount={}, lockerId={}", 
                            accountNumber, amount, lockerId, e);
                    // The lock was already created, but account save failed
                    // The transaction will rollback, but we should log this clearly
                    throw new RuntimeException("Failed to update account balance after creating lock. Transaction will be rolled back.", e);
                }
            });
        } catch (AccountAlreadyLockedException | InsufficientBalanceException ex) {
            // Re-throw business exceptions as-is
            throw ex;
        } catch (IllegalStateException ex) {
            // Re-throw version initialization errors
            log.error("Version initialization failed for account {}: {}", accountNumber, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to lock account {}: {}", accountNumber, ex.getMessage(), ex);
            // If transaction fails, the lock will be rolled back automatically
            // But we should provide a clear error message
            if (ex.getMessage() != null && (ex.getMessage().contains("commit") || 
                                            ex.getMessage().contains("Transaction"))) {
                log.warn("Transaction commit failed for account {} lock operation. " +
                        "The lock and balance update have been rolled back.", accountNumber);
            }
            throw ex;
        }
    }

    /**
     * Optimistic-lock protected unlock operation.
     * Releases the lock and restores the reserved funds to the account balance.
     */
    @Transactional
    public void unlockFunds(String accountNumber, BigDecimal amount) {
        log.info("Initiating fund unlock: accountNumber={}, amount={}", accountNumber, amount);
        
        executeWithOptimisticRetry(() -> {
            // Clean up expired locks first
            accountLockRepository.deleteExpiredLocks(OffsetDateTime.now());
            log.debug("Cleaned up expired locks before unlocking account: accountNumber={}", accountNumber);
            
            // Reload account to ensure we have the latest version
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> {
                        log.warn("Account not found for unlock: accountNumber={}", accountNumber);
                        return new IllegalArgumentException(messageSourceService.getMessage("account.not.found.unlock", accountNumber));
                    });
            
            // Ensure version is initialized
            if (account.getVersion() == null) {
                log.warn("Account has null version during unlock, initializing: accountNumber={}", accountNumber);
                accountRepository.initializeVersionIfNull(accountNumber);
                // Reload the account to get the updated version
                account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));
                if (account.getVersion() == null) {
                    account.setVersion(0L);
                    account = accountRepository.saveAndFlush(account);
                }
            }

            // Release lock row
            log.debug("Releasing account lock: accountNumber={}", accountNumber);
            accountLockRepository.findByAccountNumber(accountNumber)
                    .ifPresent(lock -> {
                        log.debug("Deleting account lock: accountNumber={}, lockedBy={}", 
                                accountNumber, lock.getLockedBy());
                        accountLockRepository.delete(lock);
                    });

            // Reload account again to ensure we have the latest version before balance update
            account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));

            // Add the amount back (compensation)
            BigDecimal oldBalance = account.getBalance();
            BigDecimal newBalance = account.getBalance().add(amount);
            account.setBalance(newBalance);
            account.setUpdatedAt(OffsetDateTime.now());
            
            accountRepository.saveAndFlush(account);
            log.info("Fund unlock completed successfully: accountNumber={}, amount={}, oldBalance={}, newBalance={}", 
                    accountNumber, amount, oldBalance, newBalance);
        });
    }

    /**
     * Releases the account lock without restoring balance.
     * Used after successful transfers where funds have already been transferred.
     * 
     * @param accountNumber The account number to release lock for
     */
    @Transactional
    public void releaseLock(String accountNumber) {
        log.info("Releasing account lock (without balance restoration): accountNumber={}", accountNumber);
        
        executeWithOptimisticRetry(() -> {
            // Clean up expired locks first
            accountLockRepository.deleteExpiredLocks(OffsetDateTime.now());
            log.debug("Cleaned up expired locks before releasing lock: accountNumber={}", accountNumber);
            
            // Release lock row only (no balance change)
            log.debug("Releasing account lock: accountNumber={}", accountNumber);
            accountLockRepository.findByAccountNumber(accountNumber)
                    .ifPresent(lock -> {
                        log.debug("Deleting account lock: accountNumber={}, lockedBy={}", 
                                accountNumber, lock.getLockedBy());
                        accountLockRepository.delete(lock);
                        log.info("Account lock released successfully: accountNumber={}, lockedBy={}", 
                                accountNumber, lock.getLockedBy());
                    });
        });
    }

    /**
     * Optimistic-lock protected credit operation.
     * Adds funds to the account balance with concurrent update protection.
     */
    @Transactional
    public void credit(String accountNumber, BigDecimal amount) {
        log.info("Initiating credit operation: accountNumber={}, amount={}", accountNumber, amount);
        
        executeWithOptimisticRetry(() -> {
            // Reload account to ensure we have the latest version
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> {
                        log.warn("Account not found for credit: accountNumber={}", accountNumber);
                        return new IllegalArgumentException(messageSourceService.getMessage("account.not.found.credit", accountNumber));
                    });
            
            // Ensure version is initialized
            if (account.getVersion() == null) {
                log.warn("Account has null version during credit, initializing: accountNumber={}", accountNumber);
                accountRepository.initializeVersionIfNull(accountNumber);
                // Reload the account to get the updated version
                account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));
                if (account.getVersion() == null) {
                    account.setVersion(0L);
                    account = accountRepository.saveAndFlush(account);
                }
            }
            
            BigDecimal oldBalance = account.getBalance();
            BigDecimal newBalance = account.getBalance().add(amount);
            account.setBalance(newBalance);
            account.setUpdatedAt(OffsetDateTime.now());
            
            accountRepository.saveAndFlush(account);
            log.info("Credit operation completed successfully: accountNumber={}, amount={}, oldBalance={}, newBalance={}", 
                    accountNumber, amount, oldBalance, newBalance);
        });
    }

    /**
     * Optimistic-lock protected debit operation.
     * Deducts funds from the account balance with concurrent update protection.
     * This is for direct debits without locking (unlike lockFunds which reserves funds).
     */
    @Transactional
    public void debit(String accountNumber, BigDecimal amount) {
        log.info("Initiating debit operation: accountNumber={}, amount={}", accountNumber, amount);
        
        executeWithOptimisticRetry(() -> {
            // Reload account to ensure we have the latest version
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> {
                        log.warn("Account not found for debit: accountNumber={}", accountNumber);
                        return new IllegalArgumentException(messageSourceService.getMessage("account.not.found.debit", accountNumber));
                    });
            
            // Ensure version is initialized
            if (account.getVersion() == null) {
                log.warn("Account has null version during debit, initializing: accountNumber={}", accountNumber);
                accountRepository.initializeVersionIfNull(accountNumber);
                // Reload the account to get the updated version
                account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(() -> new IllegalArgumentException(messageSourceService.getMessage("account.not.found", accountNumber)));
                if (account.getVersion() == null) {
                    account.setVersion(0L);
                    account = accountRepository.saveAndFlush(account);
                }
            }
            
            // Check sufficient balance
            BigDecimal currentBalance = account.getBalance();
            if (currentBalance.compareTo(amount) < 0) {
                log.warn("Insufficient balance for debit: accountNumber={}, currentBalance={}, requestedAmount={}", 
                        accountNumber, currentBalance, amount);
                throw new InsufficientBalanceException(messageSourceService.getMessage("account.insufficient.balance", accountNumber));
            }
            
            BigDecimal oldBalance = account.getBalance();
            BigDecimal newBalance = account.getBalance().subtract(amount);
            account.setBalance(newBalance);
            account.setUpdatedAt(OffsetDateTime.now());
            
            accountRepository.saveAndFlush(account);
            log.info("Debit operation completed successfully: accountNumber={}, amount={}, oldBalance={}, newBalance={}", 
                    accountNumber, amount, oldBalance, newBalance);
        });
    }

    private void executeWithOptimisticRetry(Runnable action) {
        int attempts = 0;
        int maxAttempts = 3;
        while (true) {
            try {
                action.run();
                if (attempts > 0) {
                    log.debug("Operation succeeded after {} retry attempts", attempts);
                }
                return;
            } catch (OptimisticLockingFailureException ex) {
                attempts++;
                log.debug("Optimistic locking failure detected, retrying: attempt={}/{}, error={}", 
                        attempts, maxAttempts, ex.getMessage());
                if (attempts >= maxAttempts) {
                    log.warn("Max optimistic locking retry attempts reached: maxAttempts={}", maxAttempts);
                    throw ex;
                }
                // Small delay before retry
                try {
                    long delayMs = 50L * attempts; // Exponential backoff: 50ms, 100ms, 150ms
                    log.debug("Waiting {}ms before retry attempt {}", delayMs, attempts + 1);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    log.error("Interrupted during optimistic lock retry: attempt={}", attempts);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during optimistic lock retry", ie);
                }
            } catch (NullPointerException ex) {
                // Handle null version field issues
                log.error("NullPointerException during optimistic locking - account version may be null: attempt={}, error={}", 
                        attempts, ex.getMessage(), ex);
                throw new IllegalStateException("Account version issue detected. " +
                        "Please ensure all accounts have been properly initialized with a version field. " +
                        "This typically happens when accounts are created outside of JPA or the version column is missing.", ex);
            }
        }
    }
}

