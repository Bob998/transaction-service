package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.Transaction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LedgerEntryMapper {

    @Insert("""
            INSERT INTO ledger_entry (
                entry_no,
                transaction_id,
                user_id,
                entry_type,
                direction,
                amount,
                currency
            )
            VALUES (
                #{entryNo},
                #{transaction.databaseId},
                #{transaction.userId},
                'PAYMENT',
                'DEBIT',
                #{transaction.amount},
                #{transaction.currency}
            )
            ON CONFLICT (transaction_id, entry_type) DO NOTHING
            """)
    int insertPayment(
            @Param("entryNo") String entryNo,
            @Param("transaction") Transaction transaction
    );
}
