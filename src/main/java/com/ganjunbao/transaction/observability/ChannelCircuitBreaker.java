package com.ganjunbao.transaction.observability;

import com.ganjunbao.transaction.service.ChannelHealthService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChannelCircuitBreaker {

    private final ChannelHealthService channelHealthService;
    private final BusinessMetrics metrics;
    private final MeterRegistry registry;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Map<String, CircuitState> states = new ConcurrentHashMap<>();

    public ChannelCircuitBreaker(
            ChannelHealthService channelHealthService,
            BusinessMetrics metrics,
            MeterRegistry registry,
            @Value("${transaction.channel-circuit.failure-threshold:3}")
            int failureThreshold,
            @Value("${transaction.channel-circuit.open-duration:30s}")
            Duration openDuration
    ) {
        this.channelHealthService = channelHealthService;
        this.metrics = metrics;
        this.registry = registry;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    public boolean allowRequest(String channelCode) {
        CircuitState state = state(channelCode);
        Instant openedAt = state.openedAt;
        if (openedAt == null) {
            return true;
        }
        if (openedAt.plus(openDuration).isAfter(Instant.now())) {
            return false;
        }
        reset(channelCode);
        return true;
    }

    public void recordSuccess(String channelCode) {
        CircuitState state = state(channelCode);
        boolean wasOpen = state.openedAt != null;
        state.failures.set(0);
        state.openedAt = null;
        if (wasOpen) {
            channelHealthService.markHealthy(channelCode);
        }
    }

    public void recordFailure(String channelCode) {
        CircuitState state = state(channelCode);
        int failures = state.failures.incrementAndGet();
        if (failures >= failureThreshold && state.openedAt == null) {
            state.openedAt = Instant.now();
            channelHealthService.markUnhealthy(channelCode);
            metrics.routeFailover(channelCode);
        }
    }

    public void reset(String channelCode) {
        CircuitState state = state(channelCode);
        state.failures.set(0);
        state.openedAt = null;
        channelHealthService.markHealthy(channelCode);
    }

    public Map<String, CircuitSnapshot> snapshots() {
        Map<String, CircuitSnapshot> snapshots = new ConcurrentHashMap<>();
        states.forEach((channel, state) -> snapshots.put(
                channel,
                new CircuitSnapshot(
                        state.openedAt == null ? "CLOSED" : "OPEN",
                        state.failures.get(),
                        state.openedAt
                )
        ));
        return snapshots;
    }

    private CircuitState state(String channelCode) {
        return states.computeIfAbsent(channelCode, this::newState);
    }

    private CircuitState newState(String channelCode) {
        CircuitState state = new CircuitState();
        Gauge.builder(
                        "technical.channel.circuit.open",
                        state,
                        value -> value.openedAt == null ? 0 : 1
                )
                .description("通道熔断状态，1 表示打开")
                .tag("channel", channelCode)
                .register(registry);
        Gauge.builder(
                        "technical.channel.circuit.failures",
                        state.failures,
                        AtomicInteger::get
                )
                .tag("channel", channelCode)
                .register(registry);
        return state;
    }

    private static final class CircuitState {
        private final AtomicInteger failures = new AtomicInteger();
        private volatile Instant openedAt;
    }

    public record CircuitSnapshot(
            String state,
            int failures,
            Instant openedAt
    ) {
    }
}
