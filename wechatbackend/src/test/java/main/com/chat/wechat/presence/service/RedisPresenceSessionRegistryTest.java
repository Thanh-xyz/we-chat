package main.com.chat.wechat.presence.service;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPresenceSessionRegistryTest {
	private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private final String key = "webchat:presence:sessions:" + userId;

	private StringRedisTemplate redis;
	private ZSetOperations<String, String> zset;
	private RedisPresenceSessionRegistry registry;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		zset = mock(ZSetOperations.class);
		when(redis.opsForZSet()).thenReturn(zset);
		registry = new RedisPresenceSessionRegistry(redis);
	}

	@Test
	void connectStoresSessionWithExpiryAndPrunesExpiredOnes() {
		when(zset.zCard(key)).thenReturn(1L);

		assertThat(registry.connect(userId, "s1")).isEqualTo(1);

		verify(zset).add(eq(key), eq("s1"), anyDouble());
		verify(redis).expire(key, Duration.ofSeconds(180));
		verify(zset).removeRangeByScore(eq(key), eq(Double.NEGATIVE_INFINITY), anyDouble());
	}

	@Test
	void disconnectRemovesSessionAndReportsRemainingClusterSessions() {
		when(zset.zCard(key)).thenReturn(1L, 0L);
		registry.connect(userId, "s1");

		assertThat(registry.disconnect(userId, "s1")).isZero();
		verify(zset).remove(key, "s1");
		assertThat(registry.isOnline(userId)).isFalse();
	}

	@Test
	void heartbeatRefreshesOnlyLocalSessions() {
		when(zset.zCard(key)).thenReturn(1L);
		registry.connect(userId, "s1");

		registry.heartbeat();

		verify(zset, times(2)).add(eq(key), eq("s1"), anyDouble());
	}
}
