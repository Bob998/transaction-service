package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.exception.InvalidTransactionStateException;
import com.ganjunbao.transaction.exception.TransactionNotFoundException;
import com.ganjunbao.transaction.mapper.InMemoryTransactionMapper;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import com.ganjunbao.transaction.repository.AuditLogRepository;
import com.ganjunbao.transaction.repository.TransactionAttemptRepository;
import com.ganjunbao.transaction.repository.TransactionRepository;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import com.ganjunbao.transaction.repository.UserRiskProfileRepository;
import com.ganjunbao.transaction.repository.LedgerEntryRepository;
import com.ganjunbao.transaction.redis.IdempotencyResultCache;
import com.ganjunbao.transaction.redis.RiskSlidingWindowService;
import com.ganjunbao.transaction.risk.RiskAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

public class TransactionServiceTest {
    private TransactionService transactionService;

//    @BeforeEach
//    public void setUp() {
//        TransactionRepository transactionRepository = new TransactionRepository();
//        transactionService = new TransactionService(transactionRepository);
//    }

    @BeforeEach
    public void setUp() {
        InMemoryTransactionMapper transactionMapper =
                new InMemoryTransactionMapper();

        TransactionRepository transactionRepository =
                new TransactionRepository(transactionMapper);

        PaymentChannel paymentChannel = mock(PaymentChannel.class);
        when(paymentChannel.getId()).thenReturn(1L);
        when(paymentChannel.getChannelCode()).thenReturn("DEMO");

        PaymentChannelRepository paymentChannelRepository =
                mock(PaymentChannelRepository.class);
        when(paymentChannelRepository.findActiveByCode(anyString()))
                .thenReturn(Optional.of(paymentChannel));

        TransactionAttemptRepository transactionAttemptRepository =
                mock(TransactionAttemptRepository.class);

        AuditLogRepository auditLogRepository =
                mock(AuditLogRepository.class);

        OutboxEventRepository outboxEventRepository =
                mock(OutboxEventRepository.class);

        IdempotencyResultCache idempotencyResultCache =
                mock(IdempotencyResultCache.class);
        when(idempotencyResultCache.get(anyString()))
                .thenReturn(Optional.empty());

        RiskSlidingWindowService riskSlidingWindowService =
                mock(RiskSlidingWindowService.class);

        ChannelQuotaGuard channelQuotaGuard =
                mock(ChannelQuotaGuard.class);

        DynamicChannelRouter dynamicChannelRouter =
                mock(DynamicChannelRouter.class);
        when(dynamicChannelRouter.select(anyString(),
                org.mockito.ArgumentMatchers.any(BigDecimal.class)))
                .thenReturn(new ChannelRouteDecision(
                        paymentChannel,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));

        UserRiskProfileRepository userRiskProfileRepository =
                mock(UserRiskProfileRepository.class);

        RiskAssessmentService riskAssessmentService =
                mock(RiskAssessmentService.class);

        LedgerEntryRepository ledgerEntryRepository =
                mock(LedgerEntryRepository.class);

        transactionService = new TransactionService(
                transactionRepository,
                paymentChannelRepository,
                transactionAttemptRepository,
                auditLogRepository,
                outboxEventRepository,
                idempotencyResultCache,
                riskSlidingWindowService,
                channelQuotaGuard,
                dynamicChannelRouter,
                userRiskProfileRepository,
                riskAssessmentService,
                ledgerEntryRepository
        );
    }

    @Test
    void shouldCreatePendingTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("100.50"));
        request.setDescription("购买商品");
        request.setIdempotencyKey("test-create-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction transaction = transactionService.create(request);

        assertNotNull(transaction.getId());
        assertEquals(new BigDecimal("100.50"), transaction.getAmount());
        assertEquals("购买商品", transaction.getDescription());
        assertEquals(TransactionStatus.CREATED, transaction.getStatus());
        assertNotNull(transaction.getCreatedAt());
    }

    @Test
    void shouldFindCreatedTransactionById() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("200.00"));
        request.setDescription("查询测试");
        request.setIdempotencyKey("test-find-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);
        Transaction found = transactionService.getById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals(new BigDecimal("200.00"), found.getAmount());
        assertEquals("查询测试", found.getDescription());
        assertEquals(TransactionStatus.CREATED, found.getStatus());
    }

    @Test
    void shouldConfirmPendingTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("300.00"));
        request.setDescription("确认测试");
        request.setIdempotencyKey("test-confirm-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);
        Transaction confirmed = transactionService.confirm(created.getId());

        assertEquals(TransactionStatus.RISK_APPROVED, confirmed.getStatus());
    }

    @Test
    void shouldCancelPendingTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("88.00"));
        request.setDescription("取消测试");
        request.setIdempotencyKey("test-cancel-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);
        Transaction cancelled = transactionService.cancel(created.getId());

        assertEquals(TransactionStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void shouldThrowWhenTransactionDoesNotExist() {
        assertThrows(
                TransactionNotFoundException.class,
                () -> transactionService.getById("not-exist")
        );
    }

    @Test
    void shouldRejectConfirmingConfirmedTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("500.00"));
        request.setDescription("重复确认测试");
        request.setIdempotencyKey("test-confirm-twice-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);
        transactionService.confirm(created.getId());

        assertThrows(
                InvalidTransactionStateException.class,
                () -> transactionService.confirm(created.getId())
        );
    }

    @Test
    void shouldRejectCancellingConfirmedTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("600.00"));
        request.setDescription("确认后取消测试");
        request.setIdempotencyKey("test-cancel-confirmed-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);
        transactionService.confirm(created.getId());
        transactionService.startProcessing(created.getId());

        assertThrows(
                InvalidTransactionStateException.class,
                () -> transactionService.cancel(created.getId())
        );
    }

    @Test
    void shouldCompleteSuccessfulStateFlow() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("700.00"));
        request.setDescription("完整成功流程测试");
        request.setIdempotencyKey("test-success-flow-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);

        assertEquals(
                TransactionStatus.CREATED,
                created.getStatus()
        );

        Transaction riskApproved =
                transactionService.confirm(created.getId());

        assertEquals(
                TransactionStatus.RISK_APPROVED,
                riskApproved.getStatus()
        );

        Transaction processing =
                transactionService.startProcessing(created.getId());

        assertEquals(
                TransactionStatus.PROCESSING,
                processing.getStatus()
        );

        Transaction succeeded =
                transactionService.succeed(created.getId());

        assertEquals(
                TransactionStatus.SUCCEEDED,
                succeeded.getStatus()
        );
    }

    @Test
    void shouldCompleteFailedStateFlow() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("800.00"));
        request.setDescription("完整失败流程测试");
        request.setIdempotencyKey("test-failed-flow-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);

        Transaction riskApproved =
                transactionService.confirm(created.getId());

        assertEquals(
                TransactionStatus.RISK_APPROVED,
                riskApproved.getStatus()
        );

        Transaction processing =
                transactionService.startProcessing(created.getId());

        assertEquals(
                TransactionStatus.PROCESSING,
                processing.getStatus()
        );

        Transaction failed =
                transactionService.fail(created.getId());

        assertEquals(
                TransactionStatus.FAILED,
                failed.getStatus()
        );
    }

    @Test
    void shouldRejectTransitionAfterTransactionSucceeded() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("900.00"));
        request.setDescription("成功终态测试");
        request.setIdempotencyKey("test-terminal-state-001");
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);

        transactionService.confirm(created.getId());
        transactionService.startProcessing(created.getId());
        transactionService.succeed(created.getId());

        assertThrows(
                InvalidTransactionStateException.class,
                () -> transactionService.fail(created.getId())
        );

        assertThrows(
                InvalidTransactionStateException.class,
                () -> transactionService.cancel(created.getId())
        );
    }
}
