package main.com.chat.wechat.common.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {
	@Test
	void localStoreIsBoundedEvenWhenClientsContinuouslyChangeIp() {
		InMemoryRateLimiter limiter = new InMemoryRateLimiter(100, null);
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, 60);

		for (int index = 0; index < 250; index++) {
			limiter.tryConsume("auth-login", RateLimitKey.ip("192.0.2." + (index % 250)), limit);
		}

		assertThat(limiter.size()).isLessThanOrEqualTo(100);
	}

	@Test
	void operationAndUserIdentitiesRemainIsolated() {
		InMemoryRateLimiter limiter = new InMemoryRateLimiter();
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, 60);

		assertThat(limiter.tryConsume("auth-login", RateLimitKey.user(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")), limit)).isTrue();
		assertThat(limiter.tryConsume("auth-login", RateLimitKey.user(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")), limit)).isFalse();
		assertThat(limiter.tryConsume("auth-refresh", RateLimitKey.user(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")), limit)).isTrue();
		assertThat(limiter.tryConsume("auth-login", RateLimitKey.user(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")), limit)).isTrue();
	}
}
