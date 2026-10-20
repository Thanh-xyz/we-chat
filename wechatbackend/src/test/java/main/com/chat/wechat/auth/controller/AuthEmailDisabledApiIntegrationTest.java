package main.com.chat.wechat.auth.controller;

import main.com.chat.wechat.auth.service.AuthEmailService;
import main.com.chat.wechat.auth.service.RefreshTokenGenerator;
import main.com.chat.wechat.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:auth_email_disabled;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.locations=classpath:db/test-auth-migration",
		"app.jwt.secret=test-secret-test-secret-test-secret-1234",
		"app.jwt.issuer=wechat-test",
		"app.jwt.access-token-ttl=PT15M",
		"app.jwt.refresh-token-ttl=P30D",
		"app.auth.email-enabled=false",
		"app.security.require-email-verified=false",
		"app.security.verification-email-cooldown=PT0S",
		"app.rate-limit.auth-login.capacity=200",
		"app.rate-limit.auth-register.capacity=200",
		"app.rate-limit.auth-refresh.capacity=200",
		"app.rate-limit.auth-resend-verification.capacity=200"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthEmailDisabledApiIntegrationTest {
	private static final String PASSWORD = "Password@123";
	private static final String NEW_PASSWORD = "Password@456";
	private static final String DISABLED_MESSAGE = "Tính năng email hiện tạm thời không khả dụng.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RefreshTokenGenerator refreshTokenGenerator;

	@Autowired
	private RecordingAuthEmailService emailService;

	@Test
	void registrationSkipsVerificationTokenAndEmailAndAllowsLogin() throws Exception {
		Account account = newAccount();
		String response = mockMvc.perform(post("/api/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(registerJson(account)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(account.email()))
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andExpect(jsonPath("$.verificationEmailSent").value(false))
				.andReturn()
				.getResponse()
				.getContentAsString();

		UUID userId = UUID.fromString(read(response, "$.id"));
		assertThat(jdbcTemplate.queryForObject(
				"select email_verified from users where id = ?",
				Boolean.class,
				userId)).isFalse();
		assertThat(tokenCount("email_verification_tokens", userId)).isZero();
		assertThat(emailService.verificationCalls()).isZero();
		assertThat(emailService.resetCalls()).isZero();

		mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginJson(account.email(), PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void forgotAndResendReturnSameUnavailableMessageWithoutTokensOrEmail() throws Exception {
		Account account = newAccount();
		UUID userId = register(account);
		String missingEmail = "missing-" + account.email();

		String knownForgotMessage = errorMessage("/api/auth/forgot-password", account.email());
		String missingForgotMessage = errorMessage("/api/auth/forgot-password", missingEmail);
		String knownResendMessage = errorMessage("/api/auth/resend-verification", account.email());
		String missingResendMessage = errorMessage("/api/auth/resend-verification", missingEmail);

		assertThat(knownForgotMessage).isEqualTo(DISABLED_MESSAGE);
		assertThat(missingForgotMessage).isEqualTo(knownForgotMessage);
		assertThat(knownResendMessage).isEqualTo(DISABLED_MESSAGE);
		assertThat(missingResendMessage).isEqualTo(knownResendMessage);
		assertThat(tokenCount("email_verification_tokens", userId)).isZero();
		assertThat(tokenCount("password_reset_tokens", userId)).isZero();
		assertThat(emailService.verificationCalls()).isZero();
		assertThat(emailService.resetCalls()).isZero();
	}

	@Test
	void existingVerificationAndPasswordResetTokensRemainUsable() throws Exception {
		Account account = newAccount();
		UUID userId = register(account);
		Instant now = Instant.now();
		String verificationToken = "previously-issued-verification-token";
		String resetToken = "previously-issued-reset-token";

		jdbcTemplate.update(
				"insert into email_verification_tokens (id, user_id, token_hash, expires_at, used_at, created_at) values (?, ?, ?, ?, null, ?)",
				UUID.randomUUID(),
				userId,
				refreshTokenGenerator.hash(verificationToken),
				Timestamp.from(now.plusSeconds(3600)),
				Timestamp.from(now));
		jdbcTemplate.update(
				"insert into password_reset_tokens (id, user_id, token_hash, expires_at, used_at, created_at) values (?, ?, ?, ?, null, ?)",
				UUID.randomUUID(),
				userId,
				refreshTokenGenerator.hash(resetToken),
				Timestamp.from(now.plusSeconds(3600)),
				Timestamp.from(now));

		mockMvc.perform(post("/api/auth/verify-email")
					.contentType(MediaType.APPLICATION_JSON)
					.content(tokenJson(verificationToken)))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/auth/reset-password")
					.contentType(MediaType.APPLICATION_JSON)
					.content(resetPasswordJson(resetToken, NEW_PASSWORD)))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginJson(account.email(), NEW_PASSWORD)))
				.andExpect(status().isOk());

		assertThat(emailService.verificationCalls()).isZero();
		assertThat(emailService.resetCalls()).isZero();
	}

	private UUID register(Account account) throws Exception {
		String response = mockMvc.perform(post("/api/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(registerJson(account)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return UUID.fromString(read(response, "$.id"));
	}

	private String errorMessage(String path, String email) throws Exception {
		MvcResult result = mockMvc.perform(post(path)
					.contentType(MediaType.APPLICATION_JSON)
					.content(emailJson(email)))
				.andExpect(status().isServiceUnavailable())
				.andReturn();
		return read(result.getResponse().getContentAsString(), "$.message");
	}

	private int tokenCount(String table, UUID userId) {
		return jdbcTemplate.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userId);
	}

	private Account newAccount() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		return new Account("disabled" + suffix, "disabled" + suffix + "@example.com");
	}

	private String registerJson(Account account) {
		return """
				{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\"}
				""".formatted(account.username(), account.email(), PASSWORD, account.username());
	}

	private String loginJson(String identifier, String password) {
		return """
				{\"identifier\":\"%s\",\"password\":\"%s\"}
				""".formatted(identifier, password);
	}

	private String emailJson(String email) {
		return """
				{\"email\":\"%s\"}
				""".formatted(email);
	}

	private String tokenJson(String token) {
		return """
				{\"token\":\"%s\"}
				""".formatted(token);
	}

	private String resetPasswordJson(String token, String password) {
		return """
				{\"token\":\"%s\",\"newPassword\":\"%s\"}
				""".formatted(token, password);
	}

	private record Account(String username, String email) {
	}

	@TestConfiguration
	static class EmailTestConfiguration {
		@Bean
		@Primary
		RecordingAuthEmailService recordingAuthEmailService() {
			return new RecordingAuthEmailService();
		}
	}

	static class RecordingAuthEmailService implements AuthEmailService {
		private final AtomicInteger verificationCalls = new AtomicInteger();
		private final AtomicInteger resetCalls = new AtomicInteger();

		@Override
		public void sendVerificationEmail(User user, String token, Instant expiresAt) {
			verificationCalls.incrementAndGet();
		}

		@Override
		public void sendPasswordResetEmail(User user, String token, Instant expiresAt) {
			resetCalls.incrementAndGet();
		}

		int verificationCalls() {
			return verificationCalls.get();
		}

		int resetCalls() {
			return resetCalls.get();
		}
	}
}
