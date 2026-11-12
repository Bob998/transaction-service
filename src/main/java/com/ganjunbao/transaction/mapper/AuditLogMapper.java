package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO audit_log (
                transaction_id,
                action,
                operator,
                old_value,
                new_value,
                created_at
            )
            VALUES (
                #{transactionId},
                #{action},
                #{operator},
                CAST(#{oldValue} AS JSONB),
                CAST(#{newValue} AS JSONB),
                #{createdAt}
            )
            """)
    int insert(AuditLog auditLog);

    @Select("""
            SELECT
                id,
                transaction_id,
                action,
                operator,
                old_value::text AS old_value,
                new_value::text AS new_value,
                created_at
            FROM audit_log
            WHERE transaction_id = #{transactionId}
            ORDER BY created_at
            """)
    List<AuditLog> findByTransactionId(Long transactionId);
}