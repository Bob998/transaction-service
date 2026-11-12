package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.AuditLogMapper;
import com.ganjunbao.transaction.model.AuditLog;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class AuditLogRepository {

    private final AuditLogMapper auditLogMapper;

    public AuditLogRepository(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void recordStatusChange(
            Transaction transaction,
            TransactionStatus oldStatus,
            TransactionStatus newStatus,
            String action
    ) {
        AuditLog auditLog = new AuditLog(
                transaction.getDatabaseId(),
                action,
                "SYSTEM",
                "{\"status\":\"" + oldStatus + "\"}",
                "{\"status\":\"" + newStatus + "\"}",
                OffsetDateTime.now()
        );

        auditLogMapper.insert(auditLog);
    }

    public List<AuditLog> findByTransactionId(
            Long transactionId
    ) {
        return auditLogMapper.findByTransactionId(transactionId);
    }
}