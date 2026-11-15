package com.ganjunbao.transaction.redis;

import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisDistributedLockIntegrationTest extends PostgresContainerTest {

    @Autowired
    private RedisDistributedLock distributedLock;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldAllowAnotherOwnerAfterLeaseExpires() throws Exception {
        String resource = "expiry-experiment-" + UUID.randomUUID();

        RedisLockHandle firstOwner = distributedLock.tryLock(
                resource,
                Duration.ofMillis(100)
        ).orElseThrow();

        assertThat(distributedLock.tryLock(resource, Duration.ofSeconds(5)))
                .isEmpty();

        Thread.sleep(300);

        RedisLockHandle secondOwner = distributedLock.tryLock(
                resource,
                Duration.ofSeconds(5)
        ).orElseThrow();

        assertThat(secondOwner.token()).isNotEqualTo(firstOwner.token());
        assertThat(distributedLock.unlock(firstOwner)).isFalse();
        assertThat(redisTemplate.opsForValue().get(secondOwner.key()))
                .isEqualTo(secondOwner.token());
        assertThat(distributedLock.unlock(secondOwner)).isTrue();
        assertThat(redisTemplate.hasKey(secondOwner.key())).isFalse();
    }

    @Test
    void shouldNotAllowTwoOwnersBeforeLeaseExpires() {
        String resource = "exclusive-experiment-" + UUID.randomUUID();

        RedisLockHandle firstOwner = distributedLock.tryLock(
                resource,
                Duration.ofSeconds(5)
        ).orElseThrow();
        Optional<RedisLockHandle> secondOwner = distributedLock.tryLock(
                resource,
                Duration.ofSeconds(5)
        );

        assertThat(secondOwner).isEmpty();
        assertThat(distributedLock.unlock(firstOwner)).isTrue();
    }
}
