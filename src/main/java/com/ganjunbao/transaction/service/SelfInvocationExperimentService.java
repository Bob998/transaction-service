package com.ganjunbao.transaction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SelfInvocationExperimentService {

    private final TransactionalWorkerService transactionalWorkerService;

    public SelfInvocationExperimentService(TransactionalWorkerService transactionalWorkerService) {
        this.transactionalWorkerService = transactionalWorkerService;
    }

    public boolean callTransactionalMethodInternally() {
        return transactionalMethod();
    }

    public boolean callTransactionalMethodThroughAnotherBean() {
        return transactionalWorkerService.executeInTransaction();
    }

    @Transactional
    public boolean transactionalMethod() {
        return TransactionSynchronizationManager
                .isActualTransactionActive();
    }
}
