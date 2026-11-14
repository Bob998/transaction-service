package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.TransactionAttempt;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TransactionAttemptMapper {

    @Select("""
            SELECT COALESCE(MAX(attempt_no), 0) + 1
            FROM transaction_attempt
            WHERE transaction_id = #{transactionId}
            """)
    int findNextAttemptNo(Long transactionId);

    @Insert("""
            INSERT INTO transaction_attempt (
                transaction_id,
                channel_id,
                attempt_no,
                status,
                request_data,
                response_data,
                error_code,
                error_message,
                started_at,
                finished_at
            )
            VALUES (
                #{transactionId},
                #{channelId},
                #{attemptNo},
                #{status},
                CAST(#{requestData} AS JSONB),
                CAST(#{responseData} AS JSONB),
                #{errorCode},
                #{errorMessage},
                #{startedAt},
                #{finishedAt}
            )
            """)
    int insert(TransactionAttempt attempt);

    @Select("""
            SELECT
                id,
                transaction_id,
                channel_id,
                attempt_no,
                status,
                request_data::text AS request_data,
                response_data::text AS response_data,
                error_code,
                error_message,
                started_at,
                finished_at
            FROM transaction_attempt
            WHERE transaction_id = #{transactionId}
            ORDER BY attempt_no
            """)
    List<TransactionAttempt> findByTransactionId(Long transactionId);

    @Select("""
        SELECT COUNT(*)
        FROM transaction_attempt
        WHERE transaction_id = #{transactionId}
          AND request_data ->> 'action' = 'CONFIRM'
          AND status = 'SUCCEEDED'
        """)
    int countSuccessfulConfirmAttempts(Long transactionId);
}