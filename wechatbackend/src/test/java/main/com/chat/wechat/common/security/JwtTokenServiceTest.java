package main.com.chat.wechat.common.security;

import main.com.chat.wechat.user.model.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {
	private final JwtTokenService jwtTokenService = new JwtTokenService(
			new JwtProperties("test-secret-test-secret-test-secret-1234", "wechat-test", Duration.ofMinutes(15), Duration.ofDays(30)),
			new ObjectMapper());

	@Test
	void createsAndValidatesRequiredAccessTokenClaims() {
		User user = user();

		JwtToken token = jwtTokenService.createAccessToken(user, List.of("USER"), List.of("MESSAGE_SEND"));

		JwtClaims claims = jwtTokenService.validateAccessToken(token.value()).orElseThrow();
		assertThat(claims.userId()).isEqualTo(user.id());
		assertThat(claims.username()).isEqualTo(user.username());
		assertThat(claims.roles()).containsExactly("USER");
		assertThat(claims.permissions()).containsExactly("MESSAGE_SEND");
		assertThat(claims.tokenVersion()).isEqualTo(7);
	}

	@Test
	void rejectsTamperedToken() {
		JwtToken token = jwtTokenService.createAccessToken(user(), List.of("USER"), List.of());

		String tampered = token.value().substring(0, token.value().length() - 2) + "xx";

		assertThat(jwtTokenService.validateAccessToken(tampered)).isEmpty();
	}

	private User user() {
		Instant now = Instant.now();
		return new User(
				UUID.fromString("00000000-0000-0000-0000-000000000001"),
				"user",
				"user@example.com",
				"hash",
				"User",
				null,
				"OFFLINE",
				"USER",
				true,
				"ACTIVE",
				true,
				7,
				null,
				0,
				null,
				null,
				now,
				now);
	}
}
