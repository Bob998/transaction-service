package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.PaymentChannelMapper;
import com.ganjunbao.transaction.model.ChannelStatus;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentChannelRepositoryCacheIntegrationTest extends PostgresContainerTest {

    @Autowired
    private PaymentChannelRepository paymentChannelRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private PaymentChannelMapper paymentChannelMapper;

    @Test
    void shouldReadChannelFromRedisAfterFirstDatabaseQuery() {
        String channelCode = "CACHE-" + UUID.randomUUID();
        PaymentChannel databaseChannel = new PaymentChannel(
                99L,
                channelCode,
                "缓存测试通道",
                ChannelStatus.ACTIVE
        );
        when(paymentChannelMapper.findActiveByCode(channelCode))
                .thenReturn(Optional.of(databaseChannel));

        Optional<PaymentChannel> firstResult =
                paymentChannelRepository.findActiveByCode(channelCode);
        Optional<PaymentChannel> secondResult =
                paymentChannelRepository.findActiveByCode(channelCode);

        assertThat(firstResult).isPresent();
        assertThat(secondResult).isPresent();
        assertThat(secondResult.orElseThrow().getId()).isEqualTo(99L);
        assertThat(secondResult.orElseThrow().getChannelCode())
                .isEqualTo(channelCode);
        assertThat(secondResult.orElseThrow().getChannelName())
                .isEqualTo("缓存测试通道");
        assertThat(secondResult.orElseThrow().getStatus())
                .isEqualTo(ChannelStatus.ACTIVE);

        verify(paymentChannelMapper, times(1))
                .findActiveByCode(channelCode);

        String redisKey = "route:channel:" + channelCode;
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
        assertThat(redisTemplate.getExpire(redisKey)).isPositive();
    }

    @Test
    void shouldQueryDatabaseOnlyOnceWhenCacheMissesConcurrently()
            throws Exception {
        String channelCode = "HOT-" + UUID.randomUUID();
        PaymentChannel databaseChannel = new PaymentChannel(
                100L,
                channelCode,
                "热门通道",
                ChannelStatus.ACTIVE
        );
        when(paymentChannelMapper.findActiveByCode(channelCode))
                .thenAnswer(invocation -> {
                    Thread.sleep(100);
                    return Optional.of(databaseChannel);
                });

        int requestCount = 20;
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<Optional<PaymentChannel>>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return paymentChannelRepository.findActiveByCode(channelCode);
                }));
            }

            readyLatch.await();
            startLatch.countDown();

            for (Future<Optional<PaymentChannel>> future : futures) {
                assertThat(future.get()).isPresent();
                assertThat(future.get().orElseThrow().getId()).isEqualTo(100L);
            }

            verify(paymentChannelMapper, times(1))
                    .findActiveByCode(channelCode);
        } finally {
            executor.shutdownNow();
        }
    }
}
