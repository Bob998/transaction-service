package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.exception.ChannelNotAvailableException;
import com.ganjunbao.transaction.model.ChannelHealthStatus;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import org.springframework.stereotype.Service;
import com.ganjunbao.transaction.observability.BusinessMetrics;
import org.springframework.beans.factory.annotation.Autowired;

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
    private BusinessMetrics businessMetrics;

    @Autowired
    public void setBusinessMetrics(BusinessMetrics businessMetrics) {
        this.businessMetrics = businessMetrics;
    }

    public DynamicChannelRouter(
            PaymentChannelRepository paymentChannelRepository
    ) {
        this.paymentChannelRepository = paymentChannelRepository;
    }

    public ChannelRouteDecision select(
            String currency,
            BigDecimal amount
    ) {
        ChannelRouteDecision decision = paymentChannelRepository
                .findRoutingCandidates(currency)
                .stream()
                .map(channel -> calculateDecision(channel, amount))
                .min(Comparator.comparing(ChannelRouteDecision::score))
                .orElseThrow(() ->
                        new ChannelNotAvailableException(
                                "AUTO:" + currency
                        )
                );
        if (businessMetrics != null) {
            businessMetrics.channelSelected(
                    decision.channel().getChannelCode()
            );
        }
        return decision;
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
