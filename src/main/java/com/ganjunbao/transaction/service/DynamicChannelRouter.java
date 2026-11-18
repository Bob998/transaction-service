package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.exception.ChannelNotAvailableException;
import com.ganjunbao.transaction.model.ChannelHealthStatus;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

@Service
public class DynamicChannelRouter {

    private static final BigDecimal COST_WEIGHT =
            new BigDecimal("10");

    private static final BigDecimal FAILURE_WEIGHT =
            new BigDecimal("1000");

    private static final BigDecimal DEGRADED_PENALTY =
            new BigDecimal("500");

    private final PaymentChannelRepository paymentChannelRepository;

    public DynamicChannelRouter(
            PaymentChannelRepository paymentChannelRepository
    ) {
        this.paymentChannelRepository = paymentChannelRepository;
    }

    public ChannelRouteDecision select(
            String currency,
            BigDecimal amount
    ) {
        return paymentChannelRepository
                .findRoutingCandidates(currency)
                .stream()
                .map(channel -> calculateDecision(channel, amount))
                .min(Comparator.comparing(ChannelRouteDecision::score))
                .orElseThrow(() ->
                        new ChannelNotAvailableException(
                                "AUTO:" + currency
                        )
                );
    }

    private ChannelRouteDecision calculateDecision(
            PaymentChannel channel,
            BigDecimal amount
    ) {
        BigDecimal estimatedCost = channel.getFixedCost()
                .add(amount.multiply(channel.getCostRate()))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal failureRate = BigDecimal.ONE
                .subtract(channel.getSuccessRate());

        BigDecimal score = BigDecimal
                .valueOf(channel.getPriority())
                .add(estimatedCost.multiply(COST_WEIGHT))
                .add(failureRate.multiply(FAILURE_WEIGHT));

        if (channel.getHealthStatus()
                == ChannelHealthStatus.DEGRADED) {
            score = score.add(DEGRADED_PENALTY);
        }

        return new ChannelRouteDecision(
                channel,
                estimatedCost,
                score.setScale(4, RoundingMode.HALF_UP)
        );
    }
}