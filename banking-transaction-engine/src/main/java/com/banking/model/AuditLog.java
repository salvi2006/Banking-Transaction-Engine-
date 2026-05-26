package com.banking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AuditLog — immutable record of every banking operation.
 *
 * Every deposit, withdrawal, transfer, and failed attempt is written here.
 * Records are NEVER deleted (append-only for compliance/forensics).
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Account that initiated the operation */
    @Column(nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationType operationType;

    /** Amount involved (null for balance checks) */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    /** Balance after the operation (null for failures) */
    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /** Target account for transfers */
    private String targetAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationStatus status;

    /** Human-readable description or error message */
    @Column(length = 500)
    private String description;

    /** Thread name — useful for auditing concurrent simulations */
    @Column(nullable = false)
    private String threadName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        threadName = Thread.currentThread().getName();
    }

    public AuditLog(String accountNumber, OperationType type, BigDecimal amount,
                    BigDecimal balanceAfter, String targetAccount,
                    OperationStatus status, String description) {
        this.accountNumber = accountNumber;
        this.operationType = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.targetAccountNumber = targetAccount;
        this.status = status;
        this.description = description;
    }

    public enum OperationType {
        DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN,
        BALANCE_CHECK, ACCOUNT_CREATED, PIN_CHANGE, ACCOUNT_FROZEN
    }

    public enum OperationStatus {
        SUCCESS, FAILED, REJECTED
    }
}
