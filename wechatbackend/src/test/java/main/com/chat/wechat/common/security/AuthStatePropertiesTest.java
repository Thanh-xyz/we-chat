package main.com.chat.wechat.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthStatePropertiesTest {
	@Test
	void acceptsOnlyTheConfiguredThirtyToSixtySecondTtlWindow() {
		assertThatCode(() -> new AuthStateProperties(true, "webchat:auth-state", "prod", Duration.ofSeconds(30)))
				.doesNotThrowAnyException();
		assertThatCode(() -> new AuthStateProperties(true, "webchat:auth-state", "prod", Duration.ofSeconds(60)))
				.doesNotThrowAnyException();
		assertThatThrownBy(() -> new AuthStateProperties(true, "webchat:auth-state", "prod", Duration.ofSeconds(29)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AuthStateProperties(true, "webchat:auth-state", "prod", Duration.ofSeconds(61)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsUnsafeKeyNamespaceAndEnvironment() {
		assertThatThrownBy(() -> new AuthStateProperties(true, "webchat auth", "prod", Duration.ofSeconds(30)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AuthStateProperties(true, "webchat:auth-state", "prod:shared", Duration.ofSeconds(30)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
