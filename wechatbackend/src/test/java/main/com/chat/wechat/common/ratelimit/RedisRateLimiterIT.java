package main.com.chat.wechat.common.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterIT {
	private static final String PASSWORD = "redis-rate-limit-it-password";

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>(
			DockerImageName.parse("redis:7.4.2-alpine3.21"))
			.withCommand("redis-server", "--requirepass", PASSWORD, "--appendonly", "no", "--save", "")
			.withExposedPorts(6379)
			.withStartupTimeout(Duration.ofMinutes(2));

	private LettuceConnectionFactory factoryA;
	private LettuceConnectionFactory factoryB;

	@Test
	void twoIndependentLimiterInstancesShareOneAtomicBucket() throws Exception {
		StringRedisTemplate templateA = template(true);
		StringRedisTemplate templateB = template(false);
		RateLimitProperties properties = properties();
		RedisRateLimiter limiterA = new RedisRateLimiter(templateA, properties, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
		RedisRateLimiter limiterB = new RedisRateLimiter(templateB, properties, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(2, 60);
		RateLimitKey key = RateLimitKey.ip("198.51.100.44");

		ExecutorService executor = Executors.newFixedThreadPool(4);
		CountDownLatch ready = new CountDownLatch(4);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int index = 0; index < 4; index++) {
			RedisRateLimiter limiter = index % 2 == 0 ? limiterA : limiterB;
			results.add(executor.submit(() -> {
				ready.countDown();
				assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
				return limiter.tryConsume("auth-login", key, limit);
			}));
		}
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		long allowed = 0;
		for (Future<Boolean> result : results) {
			if (result.get(10, TimeUnit.SECONDS)) {
				allowed++;
			}
		}
		executor.shutdownNow();
		assertThat(allowed).isEqualTo(2);
	}

	@Test
	void ttlAndOperationIsolationAreStoredWithoutCredentials() {
		StringRedisTemplate template = template(true);
		RateLimitProperties properties = properties();
		RedisRateLimiter limiter = new RedisRateLimiter(template, properties, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(2, 60);

		assertThat(limiter.tryConsume("ws-connect", RateLimitKey.ip("203.0.113.9"), limit)).isTrue();
		assertThat(limiter.tryConsume("ws-connect", RateLimitKey.ip("203.0.113.9"), limit)).isTrue();
		assertThat(limiter.tryConsume("ws-connect", RateLimitKey.ip("203.0.113.9"), limit)).isFalse();
		assertThat(template.getExpire(
				limiter.redisKey("ws-connect", RateLimitKey.ip("203.0.113.9")),
				TimeUnit.MILLISECONDS)).isGreaterThan(0L);
		assertThat(limiter.redisKey("ws-connect", RateLimitKey.ip("203.0.113.9")))
				.doesNotContain("RAW_TOKEN_TEST_MARKER", "Bearer", "Authorization");
		assertThat(limiter.tryConsume("auth-login", RateLimitKey.ip("203.0.113.9"), limit)).isTrue();
	}

	private StringRedisTemplate template(boolean first) {
		LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
		factory.setPassword(PASSWORD);
		factory.afterPropertiesSet();
		if (first) {
			factoryA = factory;
		} else {
			factoryB = factory;
		}
		StringRedisTemplate template = new StringRedisTemplate(factory);
		template.afterPropertiesSet();
		return template;
	}

	private RateLimitProperties properties() {
		RateLimitProperties.Limit defaultLimit = new RateLimitProperties.Limit(5, 1);
		return new RateLimitProperties(
				defaultLimit, defaultLimit, defaultLimit, new RateLimitProperties.Limit(3, 15),
				new RateLimitProperties.Limit(60, 1), new RateLimitProperties.Limit(20, 1),
				true, "webchat:ratelimit", "it", 1, 1, 10_000);
	}

	@AfterEach
	void stopResources() {
		if (factoryA != null) {
			factoryA.destroy();
			factoryA = null;
		}
		if (factoryB != null) {
			factoryB.destroy();
			factoryB = null;
		}
	}
}
