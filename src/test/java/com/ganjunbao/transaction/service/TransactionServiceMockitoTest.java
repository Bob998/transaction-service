package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.exception.InvalidTransactionStateException;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.AuditLogRepository;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import com.ganjunbao.transaction.repository.TransactionAttemptRepository;
import com.ganjunbao.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceMockitoTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PaymentChannelRepository paymentChannelRepository;

    @Mock
    private TransactionAttemptRepository transactionAttemptRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Mock
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        when(transactionRepository.findById("TXN-001"))
                .thenReturn(Optional.of(transaction));
    }

    @Test
    void shouldWriteAllRecordsWhenConfirmingTransaction() {
        Transaction updated = mock(Transaction.class);

        when(transaction.getStatus())
                .thenReturn(TransactionStatus.CREATED);
        when(transactionRepository.updateStatus(transaction))
                .thenReturn(updated);
        when(updated.getId()).thenReturn("TXN-001");

        Transaction result = transactionService.confirm("TXN-001");

        assertSame(updated, result);

        verify(transaction).approveRisk();
        verify(transactionRepository).updateStatus(transaction);
        verify(transactionAttemptRepository)
                .recordSuccess(updated, "CONFIRM");
        verify(auditLogRepository).recordStatusChange(
                updated,
                TransactionStatus.CREATED,
                TransactionStatus.RISK_APPROVED,
                "TRANSACTION_RISK_APPROVED"
        );
        verify(outboxEventRepository).record(
                "TRANSACTION",
                "TXN-001",
                "TRANSACTION_RISK_APPROVED",
                "{\"transactionId\":\"TXN-001\",\"status\":\"RISK_APPROVED\"}"
        );
    }

    @Test
    void shouldNotWriteAnythingWhenConfirmStateIsInvalid() {
        when(transaction.getStatus())
                .thenReturn(TransactionStatus.RISK_APPROVED);

        assertThrows(
                InvalidTransactionStateException.class,
                () -> transactionService.confirm("TXN-001")
        );

        verify(transaction, never()).approveRisk();
        verify(transactionRepository, never()).updateStatus(transaction);
        verifyNoInteractions(transactionAttemptRepository);
        verifyNoInteractions(auditLogRepository);
        verifyNoInteractions(outboxEventRepository);
    }
}
