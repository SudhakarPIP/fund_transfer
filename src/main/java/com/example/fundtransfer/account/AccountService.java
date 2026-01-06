package com.example.fundtransfer.account;

import com.example.fundtransfer.common.AccountAlreadyLockedException;
import com.example.fundtransfer.common.InsufficientBalanceException;
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

    public AccountService(AccountRepository accountRepository,
                          AccountLockRepository accountLockRepository) {
        this.accountRepository = accountRepository;
        this.accountLockRepository = accountLockRepository;
    }

    public AccountDto getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(AccountDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
    }

    public BigDecimal getBalance(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(Account::getBalance)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
    }

    /**
     * Optimistic-lock protected debit reservation.
     * Cleans up expired locks before checking for existing locks.
     */
    @Transactional
    public void lockFunds(String accountNumber, BigDecimal amount, String lockerId, OffsetDateTime expiry) {
        try {
            // Clean up expired locks first
            accountLockRepository.deleteExpiredLocks(OffsetDateTime.now());
            log.debug("Cleaned up expired locks before locking account {}", accountNumber);
            
            executeWithOptimisticRetry(() -> {
                // Reload account to ensure we have the latest version
                Account account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
                
                // If version is null, initialize it using a direct update query
                if (account.getVersion() == null) {
                    log.warn("Account {} has null version, initializing to 0 using direct update", accountNumber);
                    try {
                        int updated = accountRepository.initializeVersionIfNull(accountNumber);
                        if (updated > 0) {
                            // Reload the account to get the updated version
                            account = accountRepository.findByAccountNumber(accountNumber)
                                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
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
                                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
                        if (account.getVersion() == null) {
                            throw new IllegalStateException("Cannot initialize version for account " + accountNumber + 
                                    ". The account may need to be updated manually in the database.");
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
                    throw new InsufficientBalanceException("Insufficient balance for account " + accountNumber);
                }

                // create lock row
                AccountLock lock = new AccountLock();
                lock.setAccountNumber(accountNumber);
                lock.setLockedBy(lockerId);
                lock.setLockTime(OffsetDateTime.now());
                lock.setLockExpiry(expiry);
                
                try {
                    accountLockRepository.save(lock);
                    log.info("Successfully locked account {} for amount {} by {} until {}", 
                            accountNumber, amount, lockerId, expiry);
                } catch (DataIntegrityViolationException ex) {
                    // Handle unique constraint violation (account already locked by another transaction)
                    log.warn("Failed to create lock for account {} due to constraint violation: {}", 
                            accountNumber, ex.getMessage());
                    // Re-check if lock exists (might have been created by concurrent transaction)
                    Optional<AccountLock> concurrentLock = accountLockRepository.findByAccountNumber(accountNumber);
                    if (concurrentLock.isPresent()) {
                        AccountLock existing = concurrentLock.get();
                        if (existing.getLockExpiry().isAfter(OffsetDateTime.now())) {
                            throw new AccountAlreadyLockedException(accountNumber);
                        } else {
                            // Expired lock, delete and retry
                            accountLockRepository.delete(existing);
                            accountLockRepository.save(lock);
                            log.info("Removed expired concurrent lock and created new lock for account {}", accountNumber);
                        }
                    } else {
                        // Unexpected: constraint violation but no lock found
                        throw new AccountAlreadyLockedException(accountNumber);
                    }
                }

                // reduce balance to simulate reserved funds
                // Reload account to ensure we have the latest version before updating balance
                account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
                
                // Double-check version is initialized
                if (account.getVersion() == null) {
                    log.warn("Account {} version is null before balance update, re-initializing", accountNumber);
                    accountRepository.initializeVersionIfNull(accountNumber);
                    // Reload to get the updated version
                    account = accountRepository.findByAccountNumber(accountNumber)
                            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
                    if (account.getVersion() == null) {
                        throw new IllegalStateException("Failed to initialize version for account " + accountNumber);
                    }
                }
                
                BigDecimal newBalance = account.getBalance().subtract(amount);
                account.setBalance(newBalance);
                
                try {
                    accountRepository.saveAndFlush(account);
                    log.debug("Successfully updated balance for account {} to {}", accountNumber, newBalance);
                } catch (Exception e) {
                    log.error("Failed to save account {} after balance update: {}", accountNumber, e.getMessage(), e);
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

    @Transactional
    public void unlockFunds(String accountNumber, BigDecimal amount) {
        executeWithOptimisticRetry(() -> {
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
            
            // Ensure version is initialized
            if (account.getVersion() == null) {
                account.setVersion(0L);
                accountRepository.save(account);
            }

            // release lock row
            accountLockRepository.findByAccountNumber(accountNumber)
                    .ifPresent(accountLockRepository::delete);

            // add the amount back (compensation)
            account.setBalance(account.getBalance().add(amount));
            accountRepository.save(account);
        });
    }

    @Transactional
    public void credit(String accountNumber, BigDecimal amount) {
        executeWithOptimisticRetry(() -> {
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
            
            // Ensure version is initialized
            if (account.getVersion() == null) {
                account.setVersion(0L);
                accountRepository.save(account);
            }
            
            account.setBalance(account.getBalance().add(amount));
            accountRepository.save(account);
        });
    }

    private void executeWithOptimisticRetry(Runnable action) {
        int attempts = 0;
        int maxAttempts = 3;
        while (true) {
            try {
                action.run();
                return;
            } catch (OptimisticLockingFailureException ex) {
                attempts++;
                log.debug("Optimistic locking failure, attempt {}/{}", attempts, maxAttempts);
                if (attempts >= maxAttempts) {
                    log.warn("Max optimistic locking retry attempts reached");
                    throw ex;
                }
                // Small delay before retry
                try {
                    Thread.sleep(50 * attempts); // Exponential backoff: 50ms, 100ms, 150ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during optimistic lock retry", ie);
                }
            } catch (NullPointerException ex) {
                // Handle null version field issues
                log.error("NullPointerException during optimistic locking - account version may be null: {}", 
                        ex.getMessage(), ex);
                throw new IllegalStateException("Account version issue detected. " +
                        "Please ensure all accounts have been properly initialized with a version field. " +
                        "This typically happens when accounts are created outside of JPA or the version column is missing.", ex);
            }
        }
    }
}


