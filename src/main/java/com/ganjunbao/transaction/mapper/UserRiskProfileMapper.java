package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.risk.UserRiskProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface UserRiskProfileMapper {

    @Select("""
            SELECT
                id,
                user_id,
                user_level,
                daily_limit,
                status
            FROM user_risk_profile
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
            """)
    Optional<UserRiskProfile> findActiveByUserId(String userId);
}