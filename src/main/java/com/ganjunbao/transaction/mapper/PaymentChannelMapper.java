package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.model.ChannelHealthStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
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
            updated_at,
                health_status,
                priority,
                fixed_cost,
                cost_rate,
                success_rate,
                timeout_ms,
                supported_currency,
                last_health_check_at
            FROM payment_channel
            WHERE channel_code = #{channelCode}
              AND status = 'ACTIVE'
            """)
    Optional<PaymentChannel> findActiveByCode(String channelCode);

    @Select("""
            SELECT
                id,
                channel_code,
                channel_name,
                status,
                health_status,
                priority,
                fixed_cost,
                cost_rate,
                success_rate,
                timeout_ms,
                supported_currency,
                last_health_check_at,
                created_at,
                updated_at
            FROM payment_channel
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    Optional<PaymentChannel> findActiveById(Long id);

    @Select("""
        SELECT
            id,
            channel_code,
            channel_name,
            status,
            health_status,
            priority,
            fixed_cost,
            cost_rate,
            success_rate,
            timeout_ms,
            supported_currency,
            last_health_check_at,
            created_at,
            updated_at
        FROM payment_channel
        WHERE status = 'ACTIVE'
          AND health_status IN ('HEALTHY', 'DEGRADED')
          AND supported_currency = #{currency}
        ORDER BY
            CASE health_status
                WHEN 'HEALTHY' THEN 0
                WHEN 'DEGRADED' THEN 1
                ELSE 2
            END,
            priority ASC
        """)
    List<PaymentChannel> findRoutingCandidates(String currency);

    @Update("""
            UPDATE payment_channel
            SET health_status = #{healthStatus},
                last_health_check_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE channel_code = #{channelCode}
            """)
    int updateHealthStatus(
            String channelCode,
            ChannelHealthStatus healthStatus
    );
}
