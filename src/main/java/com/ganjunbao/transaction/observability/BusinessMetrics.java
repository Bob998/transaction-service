package com.ganjunbao.transaction.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void transactionCreated(String channelCode) {
        registry.counter(
                "business.transaction.created",
                "channel", channelCode
        ).increment();
    }

    public void transactionCompleted(String channelCode, String outcome) {
        registry.counter(
                "business.transaction.completed",
                "channel", channelCode,
                "outcome", outcome
        ).increment();
    }

    public void riskDecision(String decision) {
        registry.counter(
                "business.risk.decision",
                "decision", decision
        ).increment();
    }

    public void channelSelected(String channelCode) {
        registry.counter(
                "business.channel.selected",
                "channel", channelCode
        ).increment();
    }

    public void channelRequest(
            String channelCode,
            String outcome,
            Duration duration
    ) {
        Timer.builder("business.channel.request.duration")
                .description("支付通道请求耗时")
                .tag("channel", channelCode)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    public void routeFailover(String failedChannel) {
        registry.counter(
                "business.routing.failover",
                "failed_channel", failedChannel
        ).increment();
    }

    public void reconciliationDifferences(int count) {
        registry.counter("business.reconciliation.differences")
                .increment(count);
    }

    public void redisFailure(String operation) {
        registry.counter(
                "technical.redis.errors",
                "operation", operation
        ).increment();
    }
}
