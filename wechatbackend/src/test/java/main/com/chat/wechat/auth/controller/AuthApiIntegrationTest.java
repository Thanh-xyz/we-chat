package main.com.chat.wechat.auth.controller;

import main.com.chat.wechat.auth.service.AuthEmailService;
import main.com.chat.wechat.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:auth_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.locations=classpath:db/test-auth-migration",
		"app.jwt.secret=test-secret-test-secret-test-secret-1234",
		"app.jwt.issuer=wechat-test",
		"app.jwt.access-token-ttl=PT15M",
		"app.jwt.refresh-token-ttl=P30D",
		"app.security.require-email-verified=false",
		"app.security.verification-email-cooldown=PT0S",
		"app.rate-limit.auth-login.capacity=200",
		"app.rate-limit.auth-register.capacity=200",
		"app.rate-limit.auth-refresh.capacity=200",
		"app.rate-limit.auth-resend-verification.capacity=200"
})
@AutoConfigureMockMvc
class AuthApiIntegrationTest {
	private static final String PASSWORD = "Password@123";
	private static final String NEW_PASSWORD = "Password@456";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingAuthEmailService emailService;

	@Test
	void registerCreatesUserDefaultRoleAndVerificationToken() throws Exception {
		Account account = newAccount("register");

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(account)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.email").value(account.email()))
				.andExpect(jsonPath("$.emailVerified").value(false));

		assertThat(emailService.verificationToken(account.email())).isNotBlank();
	}

	@Test
	void loginSupportsEmailOrUsernameAndReturnsExpiresIn() throws Exception {
		Account account = register("login");

		TokenPair emailLogin = login(account.email(), account.password());
		TokenPair usernameLogin = login(account.username(), account.password());

		assertThat(emailLogin.accessToken()).isNotBlank();
		assertThat(usernameLogin.refreshToken()).isNotBlank();
		assertThat(emailLogin.expiresIn()).isEqualTo(900L);
	}

	@Test
	void refreshRotatesTokenAndRejectsReplay() throws Exception {
		Account account = register("refresh");
		TokenPair login = login(account.email(), account.password());

		String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson(login.refreshToken())))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		TokenPair rotated = tokens(refreshResponse);

		assertThat(rotated.refreshToken()).isNotEqualTo(login.refreshToken());

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson(login.refreshToken())))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesCurrentRefreshToken() throws Exception {
		Account account = register("logout");
		TokenPair login = login(account.email(), account.password());

		mockMvc.perform(post("/api/auth/logout")
						.header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson(login.refreshToken())))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson(login.refreshToken())))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutAllRevokesRefreshTokensAndInvalidatesAccessTokenVersion() throws Exception {
		Account account = register("logoutall");
		TokenPair login = login(account.email(), account.password());

		mockMvc.perform(post("/api/auth/logout-all")
						.header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson(login.refreshToken())))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/change-password")
						.header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(changePasswordJson(account.password(), NEW_PASSWORD)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void forgotPasswordDoesNotLeakAndResetPasswordRevokesOldCredentials() throws Exception {
		Account account = register("reset");

		mockMvc.perform(post("/api/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson("missing-" + account.email())))
				.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson(account.email())))
				.andExpect(status().isAccepted());

		String resetToken = emailService.resetToken(account.email());
		assertThat(resetToken).isNotBlank();

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(resetPasswordJson(resetToken, NEW_PASSWORD)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(account.email(), account.password())))
				.andExpect(status().isUnauthorized());

		TokenPair login = login(account.email(), NEW_PASSWORD);
		assertThat(login.accessToken()).isNotBlank();
	}

	@Test
	void verifyEmailIsOneTimeTokenAndResendDoesNotLeak() throws Exception {
		Account account = register("verify");
		String token = emailService.verificationToken(account.email());

		mockMvc.perform(post("/api/auth/resend-verification")
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson("missing-" + account.email())))
				.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content(tokenJson(token)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content(tokenJson(token)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void resendDoesNotRevokePreviouslyIssuedUnexpiredVerificationToken() throws Exception {
		Account account = register("verifyold");
		String firstToken = emailService.verificationToken(account.email());

		mockMvc.perform(post("/api/auth/resend-verification")
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson(account.email())))
				.andExpect(status().isAccepted());

		String secondToken = emailService.verificationToken(account.email());
		assertThat(secondToken).isNotEqualTo(firstToken);

		mockMvc.perform(post("/api/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content(tokenJson(firstToken)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content(tokenJson(secondToken)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void changePasswordRevokesAllRefreshTokensAndRequiresNewPassword() throws Exception {
		Account account = register("change");
		TokenPair login = login(account.email(), account.password());

		mockMvc.perform(post("/api/auth/change-password")
						.header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(changePasswordJson(account.password(), NEW_PASSWORD)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson(login.refreshToken())))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(account.email(), account.password())))
				.andExpect(status().isUnauthorized());

		TokenPair changedLogin = login(account.email(), NEW_PASSWORD);
		assertThat(changedLogin.refreshToken()).isNotBlank();
	}

	private Account register(String prefix) throws Exception {
		Account account = newAccount(prefix);
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson(account)))
				.andExpect(status().isCreated());
		return account;
	}

	private TokenPair login(String identifier, String password) throws Exception {
		String response = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(identifier, password)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return tokens(response);
	}

	private TokenPair tokens(String json) {
		return new TokenPair(read(json, "$.accessToken"), read(json, "$.refreshToken"), ((Number) read(json, "$.expiresIn")).longValue());
	}

	private Account newAccount(String prefix) {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		return new Account(prefix + suffix, prefix + suffix + "@example.com", PASSWORD);
	}

	private String registerJson(Account account) {
		return """
				{"username":"%s","email":"%s","password":"%s","displayName":"%s"}
				""".formatted(account.username(), account.email(), account.password(), account.username());
	}

	private String loginJson(String identifier, String password) {
		return """
				{"identifier":"%s","password":"%s"}
				""".formatted(identifier, password);
	}

	private String refreshJson(String refreshToken) {
		return """
				{"refreshToken":"%s"}
				""".formatted(refreshToken);
	}

	private String emailJson(String email) {
		return """
				{"email":"%s"}
				""".formatted(email);
	}

	private String resetPasswordJson(String token, String password) {
		return """
				{"token":"%s","newPassword":"%s"}
				""".formatted(token, password);
	}

	private String tokenJson(String token) {
		return """
				{"token":"%s"}
				""".formatted(token);
	}

	private String changePasswordJson(String currentPassword, String newPassword) {
		return """
				{"currentPassword":"%s","newPassword":"%s"}
				""".formatted(currentPassword, newPassword);
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private record Account(String username, String email, String password) {
	}

	private record TokenPair(String accessToken, String refreshToken, long expiresIn) {
	}

	@TestConfiguration
	static class EmailTestConfig {
		@Bean
		@Primary
		CapturingAuthEmailService capturingAuthEmailService() {
			return new CapturingAuthEmailService();
		}
	}

	static class CapturingAuthEmailService implements AuthEmailService {
		private final Map<String, String> verificationTokensByEmail = new ConcurrentHashMap<>();
		private final Map<String, String> resetTokensByEmail = new ConcurrentHashMap<>();

		@Override
		public void sendVerificationEmail(User user, String token, Instant expiresAt) {
			verificationTokensByEmail.put(user.email(), token);
		}

		@Override
		public void sendPasswordResetEmail(User user, String token, Instant expiresAt) {
			resetTokensByEmail.put(user.email(), token);
		}

		String verificationToken(String email) {
			return verificationTokensByEmail.get(email);
		}

		String resetToken(String email) {
			return resetTokensByEmail.get(email);
		}
	}
}
