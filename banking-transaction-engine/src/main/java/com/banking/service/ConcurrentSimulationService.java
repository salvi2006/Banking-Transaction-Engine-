package com.banking.service;

import com.banking.model.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentSimulationService
 *
 * Demonstrates OPTIMISTIC LOCKING in action by firing multiple transactions
 * against the same account simultaneously and showing which ones succeed
 * and which ones get an OptimisticLockException conflict (and are retried).
 *
 * This is purely a demonstration/testing utility — remove in production.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConcurrentSimulationService {

    private final TransactionService transactionService;

    /**
     * Fires `threadCount` concurrent deposits of `amount` each against the given account.
     * Returns a report of successes, conflicts, and the final balance.
     */
    public SimulationResult simulateConcurrentDeposits(String accountNumber,
                                                        BigDecimal amountPerDeposit,
                                                        int threadCount) throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);  // All threads wait, then start simultaneously
        CountDownLatch endGate   = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);
        List<String>  log_lines = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i + 1;
            executor.submit(() -> {
                try {
                    startGate.await();  // Wait for all threads to be ready
                    log.info("[SIM] Thread-{} attempting deposit of {}", threadId, amountPerDeposit);
                    transactionService.deposit(accountNumber, amountPerDeposit);
                    successes.incrementAndGet();
                    log_lines.add("Thread-" + threadId + ": SUCCESS");
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    conflicts.incrementAndGet();
                    log_lines.add("Thread-" + threadId + ": OPTIMISTIC LOCK CONFLICT (retry needed)");
                    log.warn("[SIM] Thread-{} hit optimistic lock conflict", threadId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                    log_lines.add("Thread-" + threadId + ": ERROR — " + e.getMessage());
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();            // Release all threads at once
        endGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        BigDecimal finalBalance = transactionService.getAccount(accountNumber).getBalance();

        return new SimulationResult(
                threadCount, successes.get(), conflicts.get(), failures.get(),
                finalBalance, log_lines
        );
    }

    /**
     * Simulates concurrent withdrawals to demonstrate conflict handling.
     */
    public SimulationResult simulateConcurrentWithdrawals(String accountNumber, String pin,
                                                           BigDecimal amountPerWithdrawal,
                                                           int threadCount) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);
        List<String>  logLines  = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i + 1;
            executor.submit(() -> {
                try {
                    startGate.await();
                    transactionService.withdraw(accountNumber, pin, amountPerWithdrawal);
                    successes.incrementAndGet();
                    logLines.add("Thread-" + threadId + ": WITHDRAWAL SUCCESS");
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    conflicts.incrementAndGet();
                    logLines.add("Thread-" + threadId + ": OPTIMISTIC LOCK CONFLICT");
                } catch (com.banking.exception.InsufficientFundsException e) {
                    failures.incrementAndGet();
                    logLines.add("Thread-" + threadId + ": REJECTED — Insufficient funds");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                    logLines.add("Thread-" + threadId + ": ERROR — " + e.getMessage());
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        BigDecimal finalBalance = transactionService.getAccount(accountNumber).getBalance();

        return new SimulationResult(
                threadCount, successes.get(), conflicts.get(), failures.get(),
                finalBalance, logLines
        );
    }

    // ─────────────────────────────────────────────
    //  Result DTO
    // ─────────────────────────────────────────────

    public record SimulationResult(
            int totalThreads,
            int successes,
            int optimisticLockConflicts,
            int failures,
            BigDecimal finalBalance,
            List<String> threadLog
    ) {}
}
