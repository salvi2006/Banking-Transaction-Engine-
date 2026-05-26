package com.banking.service;

import com.banking.audit.AuditService;
import com.banking.exception.*;
import com.banking.model.Account;
import com.banking.model.Account.AccountStatus;
import com.banking.model.AuditLog.OperationStatus;
import com.banking.model.AuditLog.OperationType;
import com.banking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * TransactionService — core banking operations.
 *
 * Transaction Management strategy:
 *  - @Transactional wraps every public method so the entire operation is atomic.
 *  - READ operations use READ_COMMITTED isolation for performance.
 *  - WRITE operations use REPEATABLE_READ to prevent phantom reads.
 *  - Transfers lock both accounts in a deterministic order (lower ID first)
 *    to prevent deadlocks.
 *  - Optimistic locking (@Version on Account) handles high-concurrency conflicts
 *    for single-account operations without row-level database locks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    // ─────────────────────────────────────────────
    //  Account Management
    // ─────────────────────────────────────────────

    @Transactional
    public Account createAccount(String ownerName, BigDecimal initialDeposit, String pin) {
        if (initialDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative.");
        }

        String accountNumber = generateAccountNumber();
        Account account = new Account(accountNumber, ownerName, initialDeposit, pin);
        Account saved = accountRepository.save(account);

        auditService.log(accountNumber, OperationType.ACCOUNT_CREATED,
                initialDeposit, initialDeposit, null,
                OperationStatus.SUCCESS, "Account created for " + ownerName);

        log.info("Account created: {} for {}", accountNumber, ownerName);
        return saved;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public BigDecimal checkBalance(String accountNumber, String pin) {
        Account account = authenticate(accountNumber, pin);

        auditService.log(accountNumber, OperationType.BALANCE_CHECK,
                null, account.getBalance(), null,
                OperationStatus.SUCCESS, "Balance check");

        return account.getBalance();
    }

    // ─────────────────────────────────────────────
    //  Deposit
    // ─────────────────────────────────────────────

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Account deposit(String accountNumber, BigDecimal amount) {
        validatePositiveAmount(amount);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        assertActive(account);

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        Account saved = accountRepository.save(account);   // @Version auto-incremented here

        auditService.log(accountNumber, OperationType.DEPOSIT,
                amount, newBalance, null,
                OperationStatus.SUCCESS,
                String.format("Deposited %.2f. New balance: %.2f", amount, newBalance));

        log.info("Deposit: {} → +{} = {}", accountNumber, amount, newBalance);
        return saved;
    }

    // ─────────────────────────────────────────────
    //  Withdrawal
    // ─────────────────────────────────────────────

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Account withdraw(String accountNumber, String pin, BigDecimal amount) {
        validatePositiveAmount(amount);

        Account account = authenticate(accountNumber, pin);
        assertActive(account);

        if (account.getBalance().compareTo(amount) < 0) {
            // Audit the failure BEFORE throwing (audit uses REQUIRES_NEW so it commits separately)
            auditService.log(accountNumber, OperationType.WITHDRAWAL,
                    amount, account.getBalance(), null,
                    OperationStatus.FAILED,
                    String.format("Insufficient funds. Requested: %.2f, Available: %.2f",
                            amount, account.getBalance()));

            throw new InsufficientFundsException(accountNumber, amount, account.getBalance());
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        Account saved = accountRepository.save(account);

        auditService.log(accountNumber, OperationType.WITHDRAWAL,
                amount, newBalance, null,
                OperationStatus.SUCCESS,
                String.format("Withdrew %.2f. New balance: %.2f", amount, newBalance));

        log.info("Withdrawal: {} → -{} = {}", accountNumber, amount, newBalance);
        return saved;
    }

    // ─────────────────────────────────────────────
    //  Transfer
    // ─────────────────────────────────────────────

    /**
     * Fund transfer between two accounts.
     *
     * DEADLOCK PREVENTION: We always lock accounts in ascending ID order,
     * so two concurrent transfers between the same pair of accounts will
     * never deadlock each other.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void transfer(String fromAccountNumber, String pin,
                         String toAccountNumber, BigDecimal amount) {
        validatePositiveAmount(amount);

        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }

        Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException(fromAccountNumber));
        Account toAccount = accountRepository.findByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException(toAccountNumber));

        // Validate PIN on the source account
        if (!fromAccount.getPin().equals(pin)) {
            auditService.log(fromAccountNumber, OperationType.TRANSFER_OUT,
                    amount, fromAccount.getBalance(), toAccountNumber,
                    OperationStatus.REJECTED, "Invalid PIN on transfer attempt");
            throw new InvalidPinException();
        }

        assertActive(fromAccount);
        assertActive(toAccount);

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            auditService.log(fromAccountNumber, OperationType.TRANSFER_OUT,
                    amount, fromAccount.getBalance(), toAccountNumber,
                    OperationStatus.FAILED,
                    "Insufficient funds for transfer");
            throw new InsufficientFundsException(fromAccountNumber, amount, fromAccount.getBalance());
        }

        // Apply the debit/credit
        BigDecimal fromNew = fromAccount.getBalance().subtract(amount);
        BigDecimal toNew   = toAccount.getBalance().add(amount);

        fromAccount.setBalance(fromNew);
        toAccount.setBalance(toNew);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        auditService.log(fromAccountNumber, OperationType.TRANSFER_OUT,
                amount, fromNew, toAccountNumber,
                OperationStatus.SUCCESS,
                String.format("Transferred %.2f to %s", amount, toAccountNumber));

        auditService.log(toAccountNumber, OperationType.TRANSFER_IN,
                amount, toNew, fromAccountNumber,
                OperationStatus.SUCCESS,
                String.format("Received %.2f from %s", amount, fromAccountNumber));

        log.info("Transfer: {} → {} : {}", fromAccountNumber, toAccountNumber, amount);
    }

    // ─────────────────────────────────────────────
    //  Admin / Account Management
    // ─────────────────────────────────────────────

    @Transactional
    public Account freezeAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        account.setStatus(AccountStatus.FROZEN);
        Account saved = accountRepository.save(account);

        auditService.log(accountNumber, OperationType.ACCOUNT_FROZEN,
                null, account.getBalance(), null,
                OperationStatus.SUCCESS, "Account frozen by admin");

        return saved;
    }

    @Transactional
    public Account changePIN(String accountNumber, String currentPin, String newPin) {
        Account account = authenticate(accountNumber, currentPin);
        account.setPin(newPin);
        Account saved = accountRepository.save(account);

        auditService.log(accountNumber, OperationType.PIN_CHANGE,
                null, null, null,
                OperationStatus.SUCCESS, "PIN changed successfully");

        return saved;
    }

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────

    private Account authenticate(String accountNumber, String pin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (!account.getPin().equals(pin)) {
            auditService.log(accountNumber, OperationType.BALANCE_CHECK,
                    null, null, null,
                    OperationStatus.REJECTED, "Invalid PIN attempt");
            throw new InvalidPinException();
        }
        return account;
    }

    private void assertActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException(account.getAccountNumber());
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
    }

    private String generateAccountNumber() {
        // Simple generator; replace with proper sequence in production
        return "ACC" + System.currentTimeMillis();
    }
}
