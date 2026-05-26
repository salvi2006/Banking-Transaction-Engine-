package com.banking.repository;

import com.banking.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByAccountNumberOrderByTimestampDesc(String accountNumber);

    List<AuditLog> findAllByOrderByTimestampDesc();
}
