package com.banking.audit;

import com.banking.model.AuditLog;
import com.banking.model.AuditLog.OperationStatus;
import com.banking.model.AuditLog.OperationType;
import com.banking.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * AuditService — centralized audit log writer.
 *
 * Uses Propagation.REQUIRES_NEW so audit records are committed even if
 * the parent transaction rolls back (e.g., on a failed withdrawal attempt
 * we still want a FAILED audit entry persisted).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String accountNumber, OperationType type, BigDecimal amount,
                    BigDecimal balanceAfter, String targetAccount,
                    OperationStatus status, String description) {

        AuditLog entry = new AuditLog(accountNumber, type, amount,
                balanceAfter, targetAccount, status, description);

        auditLogRepository.save(entry);

        log.info("[AUDIT] {} | {} | {} | {} | {} | Thread: {}",
                accountNumber, type, status, amount,
                description, Thread.currentThread().getName());
    }

    public List<AuditLog> getLogsForAccount(String accountNumber) {
        return auditLogRepository.findByAccountNumberOrderByTimestampDesc(accountNumber);
    }

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }
}
