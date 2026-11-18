package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.model.ChannelHealthStatus;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import org.springframework.stereotype.Service;

@Service
public class ChannelHealthService {

    private final PaymentChannelRepository paymentChannelRepository;

    public ChannelHealthService(
            PaymentChannelRepository paymentChannelRepository
    ) {
        this.paymentChannelRepository = paymentChannelRepository;
    }

    public void markHealthy(String channelCode) {
        update(channelCode, ChannelHealthStatus.HEALTHY);
    }

    public void markDegraded(String channelCode) {
        update(channelCode, ChannelHealthStatus.DEGRADED);
    }

    public void markUnhealthy(String channelCode) {
        update(channelCode, ChannelHealthStatus.UNHEALTHY);
    }

    private void update(
            String channelCode,
            ChannelHealthStatus healthStatus
    ) {
        boolean updated = paymentChannelRepository.updateHealthStatus(
                channelCode,
                healthStatus
        );
        if (!updated) {
            throw new IllegalArgumentException(
                    "支付通道不存在: " + channelCode
            );
        }
    }
}
