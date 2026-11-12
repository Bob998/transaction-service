package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.PaymentChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface PaymentChannelMapper {

    @Select("""
            SELECT
                id,
                channel_code,
                channel_name,
                status,
                created_at,
                updated_at
            FROM payment_channel
            WHERE channel_code = #{channelCode}
              AND status = 'ACTIVE'
            """)
    Optional<PaymentChannel> findActiveByCode(String channelCode);
}