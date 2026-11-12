package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.PaymentChannelMapper;
import com.ganjunbao.transaction.model.PaymentChannel;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PaymentChannelRepository {

    private final PaymentChannelMapper paymentChannelMapper;

    public PaymentChannelRepository(
            PaymentChannelMapper paymentChannelMapper
    ) {
        this.paymentChannelMapper = paymentChannelMapper;
    }

    public Optional<PaymentChannel> findActiveByCode(
            String channelCode
    ) {
        return paymentChannelMapper.findActiveByCode(channelCode);
    }
}