package main.com.chat.wechat.common.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPropertiesTest {
	@Test
	void namespaceAndEnvironmentRejectUnsafeRedisKeySegments() {
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(5, 1);

		assertThatThrownBy(() -> new RateLimitProperties(
				limit, limit, limit, limit, limit, limit,
				true, "webchat ratelimit", "prod", 1, 1, 10_000))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RateLimitProperties(
				limit, limit, limit, limit, limit, limit,
				true, "webchat:ratelimit", "prod/eu", 1, 1, 10_000))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
