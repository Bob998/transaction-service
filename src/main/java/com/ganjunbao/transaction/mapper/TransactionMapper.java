package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.Transaction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

@Mapper
public interface TransactionMapper {

    @Insert("""
            INSERT INTO payment_transaction (
                transaction_no,
                idempotency_key,
                channel_id,
                amount,
                currency,
                description,
                status,
                version,
                created_at,
                user_id,
                updated_at
            )
            VALUES (
                #{id},
                #{idempotencyKey},
                #{channelId},
                #{amount},
                #{currency},
                #{description},
                #{status},
                #{version},
                #{createdAt},
                #{userId},
                #{updatedAt}
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """)
    int insert(Transaction transaction);

    @Select("""
            SELECT
                id AS database_id,
                transaction_no,
                idempotency_key,
                channel_id,
                amount,
                currency,
                description,
                status,
                version,
                created_at,
                user_id,
                updated_at
            FROM payment_transaction
            WHERE transaction_no = #{transactionNo}
            """)
    @Results(id = "transactionResultMap", value = {
            @Result(
                    column = "database_id",
                    property = "databaseId"
            ),
            @Result(
                    column = "transaction_no",
                    property = "id",
                    id = true
            ),
            @Result(
                    column = "idempotency_key",
                    property = "idempotencyKey"
            ),
            @Result(
                    column = "channel_id",
                    property = "channelId"
            ),
            @Result(
                    column = "created_at",
                    property = "createdAt"
            ),
            @Result(
                    column = "user_id",
                    property = "userId"
            ),
            @Result(
                    column = "updated_at",
                    property = "updatedAt"
            ),
            @Result(
                    column = "version",
                    property = "version"
            )
    })
    Optional<Transaction> findByTransactionNo(String transactionNo);

    @Select("""
            SELECT
                id AS database_id,
                transaction_no,
                idempotency_key,
                channel_id,
                amount,
                currency,
                description,
                status,
                version,
                created_at,
                user_id,
                updated_at
            FROM payment_transaction
            WHERE idempotency_key = #{idempotencyKey}
            """)
    @ResultMap("transactionResultMap")
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Update("""
            UPDATE payment_transaction
            SET status = #{status},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE transaction_no = #{id}
              AND version = #{version}
            """)
    int updateStatus(Transaction transaction);

    @Select("""
        SELECT COALESCE(SUM(amount), 0)
        FROM payment_transaction
        WHERE user_id = #{userId}
          AND created_at >= CURRENT_DATE
          AND created_at < CURRENT_DATE + INTERVAL '1 day'
          AND status NOT IN ('FAILED', 'CANCELLED')
        """)
    java.math.BigDecimal sumDailyAmount(String userId);

    @Select("""
        SELECT COUNT(*)
        FROM payment_transaction
        WHERE user_id = #{userId}
          AND created_at >=
              CURRENT_TIMESTAMP - INTERVAL '10 minutes'
          AND status <> 'CANCELLED'
        """)
    long countRecentTransactions(String userId);

    @Select("""
        SELECT COUNT(*)
        FROM payment_transaction
        WHERE user_id = #{userId}
          AND created_at >=
              CURRENT_TIMESTAMP - INTERVAL '24 hours'
          AND status = 'FAILED'
        """)
    long countRecentFailures(String userId);
}
