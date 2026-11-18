package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.exception.ChannelNotAvailableException;
import com.ganjunbao.transaction.model.ChannelHealthStatus;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicChannelRouterTest {

    private PaymentChannelRepository repository;
    private DynamicChannelRouter router;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentChannelRepository.class);
        router = new DynamicChannelRouter(repository);
    }

    @Test
    void shouldChooseChannelWithLowestCombinedScore() {
        PaymentChannel fastPay = channel(
                "FAST_PAY",
                ChannelHealthStatus.HEALTHY,
                10,
                "0.30",
                "0.006000",
                "0.980000"
        );
        PaymentChannel safePay = channel(
                "SAFE_PAY",
                ChannelHealthStatus.HEALTHY,
                20,
                "0.50",
                "0.004000",
                "0.995000"
        );

        when(repository.findRoutingCandidates("CNY"))
                .thenReturn(List.of(fastPay, safePay));

        ChannelRouteDecision decision = router.select(
                "CNY",
                new BigDecimal("100.00")
        );

        assertEquals("SAFE_PAY", decision.channel().getChannelCode());
        assertEquals(new BigDecimal("0.9000"), decision.estimatedCost());
        assertEquals(new BigDecimal("34.0000"), decision.score());
    }

    @Test
    void shouldSwitchToBackupWhenPreferredChannelIsUnavailable() {
        PaymentChannel backupPay = channel(
                "BACKUP_PAY",
                ChannelHealthStatus.HEALTHY,
                100,
                "0.80",
                "0.008000",
                "0.950000"
        );

        // 首选通道已被数据库候选查询排除，只剩备用通道。
        when(repository.findRoutingCandidates("CNY"))
                .thenReturn(List.of(backupPay));

        ChannelRouteDecision decision = router.select(
                "CNY",
                new BigDecimal("100.00")
        );

        assertEquals("BACKUP_PAY", decision.channel().getChannelCode());
    }

    @Test
    void shouldRejectWhenNoChannelIsAvailable() {
        when(repository.findRoutingCandidates("USD"))
                .thenReturn(List.of());

        assertThrows(
                ChannelNotAvailableException.class,
                () -> router.select("USD", new BigDecimal("100.00"))
        );
    }

    private PaymentChannel channel(
            String code,
            ChannelHealthStatus healthStatus,
            int priority,
            String fixedCost,
            String costRate,
            String successRate
    ) {
        PaymentChannel channel = mock(PaymentChannel.class);
        when(channel.getChannelCode()).thenReturn(code);
        when(channel.getHealthStatus()).thenReturn(healthStatus);
        when(channel.getPriority()).thenReturn(priority);
        when(channel.getFixedCost()).thenReturn(new BigDecimal(fixedCost));
        when(channel.getCostRate()).thenReturn(new BigDecimal(costRate));
        when(channel.getSuccessRate()).thenReturn(
                new BigDecimal(successRate)
        );
        return channel;
    }
}
