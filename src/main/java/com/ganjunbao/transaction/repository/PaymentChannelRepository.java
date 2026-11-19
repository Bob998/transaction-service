package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.PaymentChannelMapper;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.model.ChannelHealthStatus;
import com.ganjunbao.transaction.redis.ChannelRouteCache;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Repository
public class PaymentChannelRepository {

    private final PaymentChannelMapper paymentChannelMapper;
    private final ChannelRouteCache channelRouteCache;
    private final ConcurrentHashMap<String, ReentrantLock> loadingLocks =
            new ConcurrentHashMap<>();

    public PaymentChannelRepository(
            PaymentChannelMapper paymentChannelMapper,
            ChannelRouteCache channelRouteCache
    ) {
        this.paymentChannelMapper = paymentChannelMapper;
        this.channelRouteCache = channelRouteCache;
    }

    public Optional<PaymentChannel> findActiveByCode(
            String channelCode
    ) {
        Optional<PaymentChannel> cachedChannel =
                channelRouteCache.get(channelCode);
        if (cachedChannel.isPresent()) {
            return cachedChannel;
        }

        ReentrantLock loadingLock = loadingLocks.computeIfAbsent(
                channelCode,
                ignored -> new ReentrantLock()
        );
        loadingLock.lock();
        try {
            Optional<PaymentChannel> doubleCheckedChannel =
                    channelRouteCache.get(channelCode);
            if (doubleCheckedChannel.isPresent()) {
                return doubleCheckedChannel;
            }

            Optional<PaymentChannel> databaseChannel =
                    paymentChannelMapper.findActiveByCode(channelCode);
            databaseChannel.ifPresent(channelRouteCache::put);
            return databaseChannel;
        } finally {
            loadingLock.unlock();
        }
    }

    public List<PaymentChannel> findRoutingCandidates(String currency) {
        return paymentChannelMapper.findRoutingCandidates(currency);
    }

    public Optional<PaymentChannel> findActiveById(Long id) {
        return paymentChannelMapper.findActiveById(id);
    }

    public boolean updateHealthStatus(
            String channelCode,
            ChannelHealthStatus healthStatus
    ) {
        int updatedRows = paymentChannelMapper.updateHealthStatus(
                channelCode,
                healthStatus
        );
        if (updatedRows > 0) {
            channelRouteCache.evict(channelCode);
        }
        return updatedRows > 0;
    }
}
