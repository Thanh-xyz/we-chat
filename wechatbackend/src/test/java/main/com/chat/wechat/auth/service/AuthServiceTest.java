package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.auth.dto.AuthResponse;
import main.com.chat.wechat.auth.dto.LoginRequest;
import main.com.chat.wechat.auth.repository.EmailVerificationTokenRepository;
import main.com.chat.wechat.auth.repository.PasswordResetTokenRepository;
import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.common.security.JwtProperties;
import main.com.chat.wechat.common.security.JwtToken;
import main.com.chat.wechat.common.security.JwtTokenService;
import main.com.chat.wechat.common.security.LoginSecurityProperties;
import main.com.chat.wechat.common.security.RbacProperties;
import main.com.chat.wechat.role.repository.RoleRepository;
import main.com.chat.wechat.role.repository.UserRoleRepository;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private UserRepository userRepository;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Mock
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@Mock
	private RefreshTokenGenerator refreshTokenGenerator;

	@Mock
	private JwtTokenService jwtTokenService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private PasswordPolicyValidator passwordPolicyValidator;

	@Mock
	private AuthEmailService authEmailService;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private UserRoleRepository userRoleRepository;

	@Mock
	private AuditLogService auditLogService;

	@Mock
	private AuditJsonWriter auditJsonWriter;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(
				userRepository,
				refreshTokenService,
				passwordResetTokenRepository,
				emailVerificationTokenRepository,
				refreshTokenGenerator,
				jwtTokenService,
				new JwtProperties(
						"test-secret-test-secret-test-secret-1234",
						"wechat-test",
						Duration.ofMinutes(15),
						Duration.ofDays(30)),
				passwordEncoder,
				passwordPolicyValidator,
				authEmailService,
				roleRepository,
				userRoleRepository,
				auditLogService,
				auditJsonWriter,
				new RbacProperties("USER", "SUPER_ADMIN"),
				new LoginSecurityProperties(5, Duration.ofMinutes(15), false, Duration.ofDays(1), Duration.ofMinutes(30), Duration.ofMinutes(5)));
	}

	@Test
	void wrongPasswordLocksAccountWhenMaxAttemptsReached() {
		User user = activeUser(null);
		User lockedUser = activeUser(Instant.now().plus(Duration.ofMinutes(15)));
		when(userRepository.findByUsernameOrEmail("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("bad-password", user.passwordHash())).thenReturn(false);
		when(userRepository.recordLoginFailure(eq(USER_ID), any(Instant.class), eq(5), any(Instant.class)))
				.thenReturn(lockedUser);

		assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "bad-password"), request()))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.status()).isEqualTo(HttpStatus.LOCKED));
	}

	@Test
	void lockedAccountRejectsLoginBeforePasswordCheck() {
		User lockedUser = activeUser(Instant.now().plus(Duration.ofMinutes(10)));
		when(userRepository.findByUsernameOrEmail("user@example.com")).thenReturn(Optional.of(lockedUser));

		assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "correct-password"), request()))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.status()).isEqualTo(HttpStatus.LOCKED));

		verify(passwordEncoder, never()).matches(any(), any());
	}

	@Test
	void expiredLockAllowsSuccessfulLoginAndResetsLoginState() {
		User user = activeUser(Instant.now().minus(Duration.ofMinutes(1)));
		when(userRepository.findByUsernameOrEmail("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("correct-password", user.passwordHash())).thenReturn(true);
		when(refreshTokenService.create(eq(USER_ID), any(Instant.class), any()))
				.thenReturn(new RefreshTokenService.GeneratedRefreshToken(
						"raw-refresh-token",
						"refresh-token-hash",
						Instant.now().plus(Duration.ofDays(30))));
		when(userRoleRepository.findRoleCodesByUserId(USER_ID)).thenReturn(List.of("USER"));
		when(userRoleRepository.findPermissionCodesByUserId(USER_ID)).thenReturn(List.of("CONVERSATION_READ"));
		when(jwtTokenService.createAccessToken(eq(user), eq(List.of("USER")), eq(List.of("CONVERSATION_READ"))))
				.thenReturn(new JwtToken("access-token", Instant.now().plus(Duration.ofMinutes(15))));

		AuthResponse response = authService.login(new LoginRequest("user@example.com", "correct-password"), request());

		assertThat(response.accessToken()).isEqualTo("access-token");
		verify(userRepository).recordLoginSuccess(eq(USER_ID), any(Instant.class));
		verify(refreshTokenService).create(eq(USER_ID), any(Instant.class), any());
	}

	private User activeUser(Instant lockedUntil) {
		Instant now = Instant.now();
		return new User(
				USER_ID,
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
				0,
				null,
				0,
				lockedUntil,
				null,
				now,
				now);
	}

	private MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("User-Agent", "JUnit");
		return request;
	}
}
