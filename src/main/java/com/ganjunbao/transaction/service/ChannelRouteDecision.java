package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.model.PaymentChannel;

import java.math.BigDecimal;

public record ChannelRouteDecision(
        PaymentChannel channel,
        BigDecimal estimatedCost,
        BigDecimal score
) {
}