package com.banking;

import com.banking.exception.InsufficientFundsException;
import com.banking.exception.InvalidPinException;
import com.banking.model.Account;
import com.banking.service.ConcurrentSimulationService;
import com.banking.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class BankingTransactionEngineTests {

    @Autowired
    TransactionService transactionService;

    @Autowired
    ConcurrentSimulationService simulationService;

    @Test
    void createAccount_shouldPersistWithCorrectBalance() {
        Account account = transactionService.createAccount("Test User", new BigDecimal("1000"), "0000");
        assertThat(account.getAccountNumber()).startsWith("ACC");
        assertThat(account.getBalance()).isEqualByComparingTo("1000");
        assertThat(account.getVersion()).isNotNull();   // Optimistic lock version initialized
    }

    @Test
    void deposit_shouldIncreaseBalance() {
        Account account = transactionService.createAccount("Depositor", new BigDecimal("500"), "1111");
        transactionService.deposit(account.getAccountNumber(), new BigDecimal("250"));
        BigDecimal balance = transactionService.checkBalance(account.getAccountNumber(), "1111");
        assertThat(balance).isEqualByComparingTo("750");
    }

    @Test
    void withdraw_shouldDecreaseBalance() {
        Account account = transactionService.createAccount("Withdrawer", new BigDecimal("1000"), "2222");
        transactionService.withdraw(account.getAccountNumber(), "2222", new BigDecimal("300"));
        BigDecimal balance = transactionService.checkBalance(account.getAccountNumber(), "2222");
        assertThat(balance).isEqualByComparingTo("700");
    }

    @Test
    void withdraw_withInsufficientFunds_shouldThrow() {
        Account account = transactionService.createAccount("Poor User", new BigDecimal("100"), "3333");
        assertThatThrownBy(() ->
                transactionService.withdraw(account.getAccountNumber(), "3333", new BigDecimal("500")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void withdraw_withWrongPin_shouldThrow() {
        Account account = transactionService.createAccount("Secure User", new BigDecimal("1000"), "4444");
        assertThatThrownBy(() ->
                transactionService.withdraw(account.getAccountNumber(), "0000", new BigDecimal("100")))
                .isInstanceOf(InvalidPinException.class);
    }

    @Test
    void transfer_shouldMoveFundsBetweenAccounts() {
        Account sender   = transactionService.createAccount("Sender", new BigDecimal("2000"), "5555");
        Account receiver = transactionService.createAccount("Receiver", new BigDecimal("0"), "6666");

        transactionService.transfer(sender.getAccountNumber(), "5555",
                receiver.getAccountNumber(), new BigDecimal("500"));

        assertThat(transactionService.getAccount(sender.getAccountNumber()).getBalance())
                .isEqualByComparingTo("1500");
        assertThat(transactionService.getAccount(receiver.getAccountNumber()).getBalance())
                .isEqualByComparingTo("500");
    }

    @Test
    void freezeAccount_shouldPreventWithdrawals() {
        Account account = transactionService.createAccount("Frozen User", new BigDecimal("1000"), "7777");
        transactionService.freezeAccount(account.getAccountNumber());

        assertThatThrownBy(() ->
                transactionService.withdraw(account.getAccountNumber(), "7777", new BigDecimal("100")))
                .isInstanceOf(com.banking.exception.AccountFrozenException.class);
    }

    @Test
    void concurrentDeposits_shouldHandleOptimisticLocking() throws InterruptedException {
        Account account = transactionService.createAccount("Concurrent User",
                new BigDecimal("1000"), "8888");

        // Fire 10 concurrent deposits of 100 each
        ConcurrentSimulationService.SimulationResult result =
                simulationService.simulateConcurrentDeposits(
                        account.getAccountNumber(), new BigDecimal("100"), 10);

        // Successes + conflicts should equal total threads (no silent failures)
        assertThat(result.successes() + result.optimisticLockConflicts())
                .isEqualTo(result.totalThreads());

        // Final balance should only reflect committed successes
        BigDecimal expectedBalance = new BigDecimal("1000")
                .add(new BigDecimal("100").multiply(new BigDecimal(result.successes())));
        assertThat(result.finalBalance()).isEqualByComparingTo(expectedBalance);
    }
}
