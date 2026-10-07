package main.com.chat.wechat.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitKeyTest {
	@Test
	void onlyBoundedIpAndUserIdentitiesAreRepresented() {
		assertThat(RateLimitKey.ip(" 2001:DB8::10 ").value()).isEqualTo("ip:2001:db8::10");
		assertThat(RateLimitKey.user(UUID.fromString("00000000-0000-0000-0000-000000000001")).value())
				.isEqualTo("user:00000000-0000-0000-0000-000000000001");
		assertThat(RateLimitKey.ip("Bearer RAW_TOKEN_TEST_MARKER").value()).isEqualTo("ip:unknown");
		assertThatThrownBy(() -> new RateLimitKey("eyJhbGciOiJIUzI1NiJ9.raw.signature"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
