package com.ganjunbao.transaction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionFailureExperimentService {

    private final TransactionService transactionService;

    public TransactionFailureExperimentService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Transactional
    public void confirmThenFail(String transactionId) {
        transactionService.confirm(transactionId);

        throw new IllegalStateException(
                "模拟确认交易后发生异常"
        );
    }
}