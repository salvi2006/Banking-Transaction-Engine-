package com.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Banking Transaction Engine
 *
 * Upgraded from a basic ATM console project to a production-grade Spring Boot application.
 *
 * Features:
 *  - Spring-managed transaction handling (@Transactional)
 *  - Optimistic locking via @Version to prevent lost updates under concurrency
 *  - Concurrent transaction simulation via @Async + ExecutorService
 *  - Full audit log trail for every account operation
 *  - REST API for all banking operations
 */
@SpringBootApplication
@EnableAsync
public class BankingTransactionEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingTransactionEngineApplication.class, args);
    }
}
