package com.banking.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountNumber, java.math.BigDecimal requested, java.math.BigDecimal available) {
        super(String.format("Insufficient funds in account %s: requested %.2f, available %.2f",
                accountNumber, requested, available));
    }
}
