package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.UserRiskProfileMapper;
import com.ganjunbao.transaction.risk.UserRiskProfile;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class UserRiskProfileRepository {

    private static final BigDecimal DEFAULT_DAILY_LIMIT =
            new BigDecimal("5000.00");

    private final UserRiskProfileMapper mapper;

    public UserRiskProfileRepository(
            UserRiskProfileMapper mapper
    ) {
        this.mapper = mapper;
    }

    public UserRiskProfile findOrDefault(String userId) {
        return mapper.findActiveByUserId(userId)
                .orElseGet(() -> defaultProfile(userId));
    }

    private UserRiskProfile defaultProfile(String userId) {
        return new UserRiskProfile() {
            @Override
            public String getUserId() {
                return userId;
            }

            @Override
            public String getUserLevel() {
                return "NEW";
            }

            @Override
            public BigDecimal getDailyLimit() {
                return DEFAULT_DAILY_LIMIT;
            }

            @Override
            public String getStatus() {
                return "ACTIVE";
            }
        };
    }
}