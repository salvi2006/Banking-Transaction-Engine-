package com.banking.controller;

import com.banking.audit.AuditService;
import com.banking.model.Account;
import com.banking.model.AuditLog;
import com.banking.service.ConcurrentSimulationService;
import com.banking.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BankingController — REST API for all banking operations.
 *
 * Base URL: /api/v1
 *
 * Endpoints:
 *   POST   /accounts                        → Create account
 *   GET    /accounts/{accountNumber}        → Get account details
 *   GET    /accounts/{accountNumber}/balance → Check balance (requires PIN)
 *   POST   /accounts/{accountNumber}/deposit
 *   POST   /accounts/{accountNumber}/withdraw
 *   POST   /transfers
 *   POST   /accounts/{accountNumber}/freeze
 *   POST   /accounts/{accountNumber}/change-pin
 *   GET    /audit                            → All audit logs
 *   GET    /audit/{accountNumber}            → Account audit logs
 *   POST   /simulate/deposits               → Concurrent deposit simulation
 *   POST   /simulate/withdrawals            → Concurrent withdrawal simulation
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BankingController {

    private final TransactionService transactionService;
    private final AuditService auditService;
    private final ConcurrentSimulationService simulationService;

    // ─── Account Management ───────────────────────

    @PostMapping("/accounts")
    public ResponseEntity<Account> createAccount(@Valid @RequestBody CreateAccountRequest req) {
        Account account = transactionService.createAccount(
                req.ownerName(), req.initialDeposit(), req.pin());
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(transactionService.getAccount(accountNumber));
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    public ResponseEntity<Map<String, Object>> checkBalance(
            @PathVariable String accountNumber,
            @RequestParam String pin) {
        BigDecimal balance = transactionService.checkBalance(accountNumber, pin);
        return ResponseEntity.ok(Map.of(
                "accountNumber", accountNumber,
                "balance", balance
        ));
    }

    // ─── Transactions ─────────────────────────────

    @PostMapping("/accounts/{accountNumber}/deposit")
    public ResponseEntity<Account> deposit(
            @PathVariable String accountNumber,
            @Valid @RequestBody AmountRequest req) {
        return ResponseEntity.ok(transactionService.deposit(accountNumber, req.amount()));
    }

    @PostMapping("/accounts/{accountNumber}/withdraw")
    public ResponseEntity<Account> withdraw(
            @PathVariable String accountNumber,
            @Valid @RequestBody WithdrawRequest req) {
        return ResponseEntity.ok(transactionService.withdraw(accountNumber, req.pin(), req.amount()));
    }

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, String>> transfer(@Valid @RequestBody TransferRequest req) {
        transactionService.transfer(
                req.fromAccountNumber(), req.pin(),
                req.toAccountNumber(), req.amount());
        return ResponseEntity.ok(Map.of(
                "message", "Transfer successful",
                "from", req.fromAccountNumber(),
                "to", req.toAccountNumber(),
                "amount", req.amount().toString()
        ));
    }

    // ─── Admin ────────────────────────────────────

    @PostMapping("/accounts/{accountNumber}/freeze")
    public ResponseEntity<Account> freezeAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(transactionService.freezeAccount(accountNumber));
    }

    @PostMapping("/accounts/{accountNumber}/change-pin")
    public ResponseEntity<Map<String, String>> changePin(
            @PathVariable String accountNumber,
            @Valid @RequestBody ChangePinRequest req) {
        transactionService.changePIN(accountNumber, req.currentPin(), req.newPin());
        return ResponseEntity.ok(Map.of("message", "PIN changed successfully"));
    }

    // ─── Audit Logs ───────────────────────────────

    @GetMapping("/audit")
    public ResponseEntity<List<AuditLog>> getAllAuditLogs() {
        return ResponseEntity.ok(auditService.getAllLogs());
    }

    @GetMapping("/audit/{accountNumber}")
    public ResponseEntity<List<AuditLog>> getAccountAuditLogs(@PathVariable String accountNumber) {
        return ResponseEntity.ok(auditService.getLogsForAccount(accountNumber));
    }

    // ─── Concurrency Simulation ───────────────────

    @PostMapping("/simulate/deposits")
    public ResponseEntity<ConcurrentSimulationService.SimulationResult> simulateDeposits(
            @Valid @RequestBody SimulationRequest req) throws InterruptedException {
        ConcurrentSimulationService.SimulationResult result =
                simulationService.simulateConcurrentDeposits(
                        req.accountNumber(), req.amountPerOperation(), req.threadCount());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/simulate/withdrawals")
    public ResponseEntity<ConcurrentSimulationService.SimulationResult> simulateWithdrawals(
            @Valid @RequestBody SimulationWithPinRequest req) throws InterruptedException {
        ConcurrentSimulationService.SimulationResult result =
                simulationService.simulateConcurrentWithdrawals(
                        req.accountNumber(), req.pin(), req.amountPerOperation(), req.threadCount());
        return ResponseEntity.ok(result);
    }

    // ─── Request DTOs ─────────────────────────────

    public record CreateAccountRequest(
            @NotBlank String ownerName,
            @NotNull @DecimalMin("0.00") BigDecimal initialDeposit,
            @NotBlank @Size(min = 4, max = 6) String pin
    ) {}

    public record AmountRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}

    public record WithdrawRequest(
            @NotBlank String pin,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}

    public record TransferRequest(
            @NotBlank String fromAccountNumber,
            @NotBlank String pin,
            @NotBlank String toAccountNumber,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}

    public record ChangePinRequest(
            @NotBlank String currentPin,
            @NotBlank @Size(min = 4, max = 6) String newPin
    ) {}

    public record SimulationRequest(
            @NotBlank String accountNumber,
            @NotNull @DecimalMin("0.01") BigDecimal amountPerOperation,
            @Min(2) @Max(50) int threadCount
    ) {}

    public record SimulationWithPinRequest(
            @NotBlank String accountNumber,
            @NotBlank String pin,
            @NotNull @DecimalMin("0.01") BigDecimal amountPerOperation,
            @Min(2) @Max(50) int threadCount
    ) {}
}
