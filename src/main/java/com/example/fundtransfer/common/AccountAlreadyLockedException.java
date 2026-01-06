package com.example.fundtransfer.common;

public class AccountAlreadyLockedException extends RuntimeException {

    private final String accountNumber;

    public AccountAlreadyLockedException(String accountNumber) {
        super("Account already locked: " + accountNumber);
        this.accountNumber = accountNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
}


