package com.example.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AccountLockRepository extends JpaRepository<AccountLock, Long> {
    // Unique lock per account
    Optional<AccountLock> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);
    
    @Modifying
    @Query("DELETE FROM AccountLock al WHERE al.lockExpiry < :now")
    void deleteExpiredLocks(@Param("now") OffsetDateTime now);
}

