package com.example.account;

import java.math.BigDecimal;

public class AccountDto {

    private String accountNumber;
    private Long customerId;
    private BigDecimal balance;
    private String currency;
    private String status;

    public AccountDto(String accountNumber, Long customerId, BigDecimal balance, String currency, String status) {
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public static AccountDto from(Account account) {
        return new AccountDto(
                account.getAccountNumber(),
                account.getCustomerId(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus().name()
        );
    }
}

