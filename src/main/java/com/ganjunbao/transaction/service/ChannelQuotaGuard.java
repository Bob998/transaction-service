package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.exception.ChannelQuotaExceededException;
import com.ganjunbao.transaction.exception.ChannelQuotaUnavailableException;
import com.ganjunbao.transaction.redis.ChannelQuotaService;
import com.ganjunbao.transaction.redis.QuotaReservationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ChannelQuotaGuard {

    private final ChannelQuotaService channelQuotaService;
    private final BigDecimal dailyLimit;

    public ChannelQuotaGuard(
            ChannelQuotaService channelQuotaService,
            @Value("${transaction.channel-quota.daily-limit:1000000.00}")
            BigDecimal dailyLimit
    ) {
        this.channelQuotaService = channelQuotaService;
        this.dailyLimit = dailyLimit;
    }

    public void reserve(
            String channelCode,
            BigDecimal amount,
            String idempotencyKey
    ) {
        QuotaReservationResult result;
        try {
            result = channelQuotaService.tryReserve(
                    channelCode,
                    amount,
                    dailyLimit,
                    idempotencyKey
            );
        } catch (DataAccessException exception) {
            throw new ChannelQuotaUnavailableException(exception);
        }

        if (!result.allowed()) {
            throw new ChannelQuotaExceededException(
                    channelCode,
                    result.remainingAmount()
            );
        }
    }
}
