package com.ganjunbao.transaction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TransactionalWorkerService {

    @Transactional
    public boolean executeInTransaction() {
        return TransactionSynchronizationManager
                .isActualTransactionActive();
    }
}