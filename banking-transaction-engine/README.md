# Banking Transaction Engine 🏦

A production-grade **Banking Transaction Engine** built with Spring Boot, upgraded from a basic ATM console project.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 🚀 What Was Upgraded

| Original ATM Project | This Banking Transaction Engine |
|---|---|
| Console-based Java | REST API with Spring Boot |
| No transaction safety | `@Transactional` with isolation levels |
| Single-threaded | Concurrent transaction simulation |
| No concurrency protection | Optimistic Locking (`@Version`) |
| No history | Full audit log trail |
| Plain OOP | Spring-managed beans & DI |

---

## 🏗️ Architecture

```
com.banking/
├── BankingTransactionEngineApplication.java   # Entry point
├── config/
│   ├── AsyncConfig.java                       # Thread pool for @Async
│   └── DataSeeder.java                        # Demo data on startup
├── model/
│   ├── Account.java                           # @Version optimistic lock
│   └── AuditLog.java                          # Immutable audit records
├── repository/
│   ├── AccountRepository.java                 # JPA + pessimistic lock query
│   └── AuditLogRepository.java
├── service/
│   ├── TransactionService.java                # Core banking logic + @Transactional
│   └── ConcurrentSimulationService.java       # Concurrency demo with CountDownLatch
├── audit/
│   └── AuditService.java                      # REQUIRES_NEW propagation
├── controller/
│   └── BankingController.java                 # REST endpoints
└── exception/
    ├── GlobalExceptionHandler.java            # Unified error responses
    ├── AccountNotFoundException.java
    ├── AccountFrozenException.java
    ├── InsufficientFundsException.java
    └── InvalidPinException.java
```

---

## 🔑 Key Concepts Demonstrated

### 1. `@Transactional` — Transaction Management
Every banking operation is wrapped in a Spring-managed transaction:
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public Account withdraw(String accountNumber, String pin, BigDecimal amount) { ... }
```
- **REPEATABLE_READ** for write operations (prevents phantom reads during a transaction)
- **READ_COMMITTED** for read-only operations (better performance)
- **Automatic rollback** on any unchecked exception

### 2. Optimistic Locking — `@Version`
The `Account` entity has a `@Version` field:
```java
@Version
private Long version;
```
- Every `UPDATE` to an account increments the version
- If two concurrent transactions read version `5` and both try to save, the second one gets `ObjectOptimisticLockingFailureException`
- **No database row locks needed** — scales to thousands of concurrent reads

### 3. Deadlock Prevention in Transfers
Transfers always lock accounts in **ascending ID order**, so two concurrent transfers between the same pair never deadlock each other.

### 4. Concurrent Transaction Simulation
The `/api/v1/simulate/deposits` endpoint fires N threads simultaneously using a `CountDownLatch` start gate:
```
Thread-1: SUCCESS
Thread-2: OPTIMISTIC LOCK CONFLICT  ← automatically detected and reported
Thread-3: SUCCESS
...
```

### 5. Audit Log with `REQUIRES_NEW` Propagation
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(...) { ... }
```
Audit records are committed **in their own transaction**, so a failed operation still produces a `FAILED` audit entry.

---

## ▶️ Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+

### Run
```bash
git clone https://github.com/YOUR_USERNAME/Banking-Transaction-Engine-.git
cd Banking-Transaction-Engine-
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

Demo accounts are created automatically:

| Owner | PIN | Starting Balance |
|---|---|---|
| Alice Wonderland | 1234 | 10,000 |
| Bob Builder | 5678 | 5,000 |
| Carol Danvers | 9999 | 25,000 |

### H2 Console (in-memory DB viewer)
`http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:mem:bankingdb`

---

## 📡 API Reference

### Create Account
```http
POST /api/v1/accounts
Content-Type: application/json

{ "ownerName": "Jane Doe", "initialDeposit": 1000, "pin": "1234" }
```

### Check Balance
```http
GET /api/v1/accounts/{accountNumber}/balance?pin=1234
```

### Deposit
```http
POST /api/v1/accounts/{accountNumber}/deposit
{ "amount": 500 }
```

### Withdraw
```http
POST /api/v1/accounts/{accountNumber}/withdraw
{ "pin": "1234", "amount": 200 }
```

### Transfer
```http
POST /api/v1/transfers
{ "fromAccountNumber": "ACC123", "pin": "1234", "toAccountNumber": "ACC456", "amount": 300 }
```

### View Audit Log
```http
GET /api/v1/audit/{accountNumber}
```

### Run Concurrent Simulation
```http
POST /api/v1/simulate/deposits
{ "accountNumber": "ACC123", "amountPerOperation": 100, "threadCount": 10 }
```

---

## 🧪 Running Tests
```bash
mvn test
```

Tests cover: account creation, deposit, withdrawal, transfer, PIN validation, frozen account handling, and concurrent optimistic locking.

---

## 🔮 Production Upgrade Path
- Replace H2 with PostgreSQL/MySQL
- Hash PINs with BCrypt (`PasswordEncoder`)
- Add Spring Security / JWT authentication
- Add Flyway for schema migrations
- Add Prometheus metrics (`micrometer`)
- Deploy with Docker + Kubernetes

---

## 📄 License
MIT — forked from [kishanrajput23/Java-Projects-Collections](https://github.com/kishanrajput23/Java-Projects-Collections)
