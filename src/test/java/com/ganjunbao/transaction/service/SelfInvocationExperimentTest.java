package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SelfInvocationExperimentTest extends PostgresContainerTest {

    @Autowired
    private SelfInvocationExperimentService experimentService;

    @Test
    void shouldStartTransactionWhenCalledThroughSpringProxy() {
        boolean transactionActive =
                experimentService.transactionalMethod();

        assertTrue(transactionActive);
    }

    @Test
    void shouldNotStartTransactionDuringSelfInvocation() {
        boolean transactionActive =
                experimentService.callTransactionalMethodInternally();

        assertFalse(transactionActive);
    }

    @Test
    void shouldStartTransactionWhenCallingAnotherBean() {
        boolean transactionActive =
                experimentService
                        .callTransactionalMethodThroughAnotherBean();

        assertTrue(transactionActive);
    }
}
