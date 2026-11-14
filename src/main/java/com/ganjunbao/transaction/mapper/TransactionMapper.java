package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.Transaction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
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
                #{updatedAt}
            )
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
                    column = "updated_at",
                    property = "updatedAt"
            ),
            @Result(
                    column = "version",
                    property = "version"
            )
    })
    Optional<Transaction> findByTransactionNo(String transactionNo);

    @Update("""
            UPDATE payment_transaction
            SET status = #{status},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE transaction_no = #{id}
              AND version = #{version}
            """)
    int updateStatus(Transaction transaction);
}
