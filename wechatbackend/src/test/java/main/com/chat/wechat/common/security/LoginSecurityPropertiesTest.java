package main.com.chat.wechat.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginSecurityPropertiesTest {
	@Test
	void rejectsEmailVerificationTtlThatWouldExpireImmediately() {
		assertThatThrownBy(() -> new LoginSecurityProperties(
				3,
				Duration.ofMinutes(15),
				false,
				Duration.ofMillis(24),
				Duration.ofMinutes(15),
				Duration.ofMinutes(5)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("email-verification-token-ttl");
	}

	@Test
	void defaultsUseProductionSafeTokenTtls() {
		LoginSecurityProperties properties = new LoginSecurityProperties(
				0,
				null,
				false,
				null,
				null,
				null);

		assertThat(properties.maxFailedLoginAttempts()).isEqualTo(3);
		assertThat(properties.emailVerificationTokenTtl()).isEqualTo(Duration.ofHours(24));
		assertThat(properties.passwordResetTokenTtl()).isEqualTo(Duration.ofMinutes(15));
	}
}
