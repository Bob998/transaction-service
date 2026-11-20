package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.reconciliation.ReconciliationCandidate;
import com.ganjunbao.transaction.reconciliation.ReconciliationDifference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ReconciliationMapper {

    @Select("""
            INSERT INTO reconciliation_batch (business_date, status)
            VALUES (#{businessDate}, 'PROCESSING')
            RETURNING id
            """)
    long createBatch(LocalDate businessDate);

    @Select("""
            SELECT transaction_no, amount
            FROM payment_transaction
            WHERE status = 'SUCCEEDED'
              AND created_at >= #{startAt}
              AND created_at < #{endAt}
            ORDER BY transaction_no
            """)
    List<ReconciliationCandidate> findSuccessfulTransactions(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt
    );

    @Insert("""
            INSERT INTO reconciliation_difference (
                batch_id,
                transaction_no,
                difference_type,
                local_amount,
                channel_amount
            )
            VALUES (
                #{batchId},
                #{transactionNo},
                #{differenceType},
                #{localAmount},
                #{channelAmount}
            )
            """)
    int insertDifference(
            @Param("batchId") long batchId,
            @Param("transactionNo") String transactionNo,
            @Param("differenceType") String differenceType,
            @Param("localAmount") BigDecimal localAmount,
            @Param("channelAmount") BigDecimal channelAmount
    );

    @Update("""
            UPDATE reconciliation_batch
            SET status = 'COMPLETED',
                local_record_count = #{localCount},
                channel_record_count = #{channelCount},
                difference_count = #{differenceCount},
                completed_at = CURRENT_TIMESTAMP
            WHERE id = #{batchId}
            """)
    int completeBatch(
            @Param("batchId") long batchId,
            @Param("localCount") int localCount,
            @Param("channelCount") int channelCount,
            @Param("differenceCount") int differenceCount
    );

    @Select("""
            SELECT
                rd.id,
                pt.id AS transaction_id,
                rd.transaction_no,
                rd.difference_type,
                rd.local_amount,
                rd.channel_amount,
                rd.status
            FROM reconciliation_difference rd
            LEFT JOIN payment_transaction pt
              ON pt.transaction_no = rd.transaction_no
            WHERE rd.id = #{differenceId}
            """)
    Optional<ReconciliationDifference> findDifferenceById(long differenceId);

    @Update("""
            UPDATE reconciliation_difference
            SET status = #{resolution},
                resolved_by = #{operator},
                resolution_note = #{note},
                resolved_at = CURRENT_TIMESTAMP
            WHERE id = #{differenceId}
              AND status = 'OPEN'
            """)
    int resolveDifference(
            @Param("differenceId") long differenceId,
            @Param("resolution") String resolution,
            @Param("operator") String operator,
            @Param("note") String note
    );
}
