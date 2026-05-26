package com.banking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account entity.
 *
 * The @Version field is the heart of OPTIMISTIC LOCKING:
 *   - Every UPDATE increments the version column.
 *   - If two concurrent transactions read the same version and both try to write,
 *     the second one will throw OptimisticLockException — preventing a lost update.
 *   - No database-level row locks needed; high throughput for concurrent reads.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String accountNumber;

    @NotBlank
    @Column(nullable = false)
    private String ownerName;

    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private String pin;   // In production: store BCrypt hash, never plaintext

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Optimistic locking version column — auto-managed by JPA/Hibernate */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Account(String accountNumber, String ownerName, BigDecimal initialBalance, String pin) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.balance = initialBalance;
        this.pin = pin;
        this.status = AccountStatus.ACTIVE;
    }

    public enum AccountStatus {
        ACTIVE, FROZEN, CLOSED
    }
}
