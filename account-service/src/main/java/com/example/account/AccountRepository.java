package com.example.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    
    @Modifying
    @Query("UPDATE Account a SET a.version = 0 WHERE a.accountNumber = :accountNumber AND a.version IS NULL")
    int initializeVersionIfNull(@Param("accountNumber") String accountNumber);
}

