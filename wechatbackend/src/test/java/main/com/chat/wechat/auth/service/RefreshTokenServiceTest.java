package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.auth.model.RefreshToken;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.common.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private RefreshTokenGenerator refreshTokenGenerator;

	@Test
	void createStoresOnlyHashWithDeviceAndIpMetadata() {
		RefreshTokenService service = new RefreshTokenService(
				refreshTokenRepository,
				refreshTokenGenerator,
				new JwtProperties("test-secret-test-secret-test-secret-1234", "wechat-test", Duration.ofMinutes(15), Duration.ofDays(30)));
		Instant now = Instant.parse("2026-06-10T10:00:00Z");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("User-Agent", "JUnit");
		request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");
		when(refreshTokenGenerator.generate()).thenReturn("raw-token");
		when(refreshTokenGenerator.hash("raw-token")).thenReturn("token-hash");

		RefreshTokenService.GeneratedRefreshToken generated = service.create(USER_ID, now, request);

		ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(tokenCaptor.capture());
		RefreshToken stored = tokenCaptor.getValue();
		assertThat(generated.rawToken()).isEqualTo("raw-token");
		assertThat(generated.tokenHash()).isEqualTo("token-hash");
		assertThat(stored.tokenHash()).isEqualTo("token-hash");
		assertThat(stored.tokenHash()).isNotEqualTo("raw-token");
		assertThat(stored.deviceInfo()).isEqualTo("JUnit");
		assertThat(stored.ipAddress()).isEqualTo("203.0.113.10");
		assertThat(stored.expiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
	}
}
