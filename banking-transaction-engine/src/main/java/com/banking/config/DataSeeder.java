package com.banking.config;

import com.banking.model.Account;
import com.banking.repository.AccountRepository;
import com.banking.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * DataSeeder — creates demo accounts on application startup.
 * Remove or replace with Flyway migrations in production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final TransactionService transactionService;

    @Override
    public void run(String... args) {
        log.info("=== Banking Transaction Engine — Seeding demo data ===");

        Account alice = transactionService.createAccount("Alice Wonderland", new BigDecimal("10000.00"), "1234");
        Account bob   = transactionService.createAccount("Bob Builder",      new BigDecimal("5000.00"),  "5678");
        Account carol = transactionService.createAccount("Carol Danvers",    new BigDecimal("25000.00"), "9999");

        log.info("Demo accounts created:");
        log.info("  Alice → {} | Balance: 10000 | PIN: 1234", alice.getAccountNumber());
        log.info("  Bob   → {} | Balance: 5000  | PIN: 5678", bob.getAccountNumber());
        log.info("  Carol → {} | Balance: 25000 | PIN: 9999", carol.getAccountNumber());
        log.info("H2 Console: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:mem:bankingdb)");
        log.info("API base:   http://localhost:8080/api/v1");
        log.info("======================================================");
    }
}
