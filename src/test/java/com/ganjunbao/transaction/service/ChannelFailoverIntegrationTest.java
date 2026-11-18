package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ChannelFailoverIntegrationTest extends PostgresContainerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DynamicChannelRouter dynamicChannelRouter;

    @Autowired
    private ChannelHealthService channelHealthService;

    @Test
    void shouldSwitchToBackupWhenPreferredChannelBecomesUnhealthy() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String preferredCode = "PREFERRED_" + suffix;
        String backupCode = "BACKUP_" + suffix;

        insertChannel(preferredCode, "故障切换首选通道", 1, "0.99");
        insertChannel(backupCode, "故障切换备用通道", 100, "0.90");

        ChannelRouteDecision beforeFailure = dynamicChannelRouter.select(
                "FLR",
                new BigDecimal("100.00")
        );
        assertEquals(
                preferredCode,
                beforeFailure.channel().getChannelCode()
        );

        channelHealthService.markUnhealthy(preferredCode);

        ChannelRouteDecision afterFailure = dynamicChannelRouter.select(
                "FLR",
                new BigDecimal("100.00")
        );
        assertEquals(
                backupCode,
                afterFailure.channel().getChannelCode()
        );
    }

    private void insertChannel(
            String channelCode,
            String channelName,
            int priority,
            String successRate
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_channel (
                    channel_code,
                    channel_name,
                    status,
                    health_status,
                    priority,
                    fixed_cost,
                    cost_rate,
                    success_rate,
                    supported_currency
                )
                VALUES (?, ?, 'ACTIVE', 'HEALTHY', ?, 0, 0, ?, 'FLR')
                """,
                channelCode,
                channelName,
                priority,
                new BigDecimal(successRate)
        );
    }
}
