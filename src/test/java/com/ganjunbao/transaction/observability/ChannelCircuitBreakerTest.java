package com.ganjunbao.transaction.observability;

import com.ganjunbao.transaction.service.ChannelHealthService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChannelCircuitBreakerTest {

    @Test
    void shouldOpenCircuitAndMarkChannelUnhealthyAfterThreshold() {
        ChannelHealthService healthService = mock(ChannelHealthService.class);
        BusinessMetrics metrics = mock(BusinessMetrics.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChannelCircuitBreaker circuitBreaker = new ChannelCircuitBreaker(
                healthService,
                metrics,
                registry,
                2,
                Duration.ofMinutes(1)
        );

        circuitBreaker.recordFailure("SAFE_PAY");
        assertTrue(circuitBreaker.allowRequest("SAFE_PAY"));

        circuitBreaker.recordFailure("SAFE_PAY");
        assertFalse(circuitBreaker.allowRequest("SAFE_PAY"));

        verify(healthService).markUnhealthy("SAFE_PAY");
        verify(metrics).routeFailover("SAFE_PAY");
    }
}
