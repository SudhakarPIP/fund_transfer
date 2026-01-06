package com.example.fundtransfer.account;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "account_locks", 
       uniqueConstraints = @UniqueConstraint(name = "uk_account_lock", columnNames = "account_number"))
public class AccountLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "locked_by", nullable = false)
    private String lockedBy;

    @Column(name = "lock_time", nullable = false)
    private OffsetDateTime lockTime = OffsetDateTime.now();

    @Column(name = "lock_expiry", nullable = false)
    private OffsetDateTime lockExpiry;

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public OffsetDateTime getLockTime() {
        return lockTime;
    }

    public void setLockTime(OffsetDateTime lockTime) {
        this.lockTime = lockTime;
    }

    public OffsetDateTime getLockExpiry() {
        return lockExpiry;
    }

    public void setLockExpiry(OffsetDateTime lockExpiry) {
        this.lockExpiry = lockExpiry;
    }
}


