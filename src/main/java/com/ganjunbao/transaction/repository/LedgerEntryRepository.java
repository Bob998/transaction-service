package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.LedgerEntryMapper;
import com.ganjunbao.transaction.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class LedgerEntryRepository {

    private final LedgerEntryMapper ledgerEntryMapper;

    public LedgerEntryRepository(LedgerEntryMapper ledgerEntryMapper) {
        this.ledgerEntryMapper = ledgerEntryMapper;
    }

    public void recordPayment(Transaction transaction) {
        ledgerEntryMapper.insertPayment(
                "LEDGER-" + UUID.randomUUID(),
                transaction
        );
    }
}
