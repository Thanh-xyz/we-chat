package main.com.chat.wechat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:cors_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.locations=classpath:db/test-auth-migration",
		"app.jwt.secret=test-secret-test-secret-test-secret-1234",
		"app.jwt.issuer=wechat-test",
		"app.jwt.access-token-ttl=PT15M",
		"app.jwt.refresh-token-ttl=P30D",
		"app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsApiIntegrationTest {
	private static final String ALLOWED_ORIGIN = "http://localhost:5173";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allowedOriginPreflightCompletesBeforeAuthentication() throws Exception {
		var response = mockMvc.perform(options("/api/users/me")
					.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-request-id"))
				.andReturn()
				.getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED_ORIGIN);
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("GET");
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).toLowerCase())
				.contains("authorization", "content-type", "x-request-id");
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
	}

	@Test
	void allowedOriginCanCallRestApiWithAuthorizationHeaderPolicy() throws Exception {
		var response = mockMvc.perform(get("/api/users/me")
					.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
					.header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
				.andReturn()
				.getResponse();

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED_ORIGIN);
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)).contains("X-Request-ID");
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://evil.example",
			"null",
			"http://localhost:3000",
			"http://127.0.0.1:5173",
			"https://localhost:5173",
			"http://localhost:5173.evil.com"
	})
	void disallowedOriginCannotAuthorizePreflight(String origin) throws Exception {
		var response = mockMvc.perform(options("/api/users/me")
					.header(HttpHeaders.ORIGIN, origin)
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
				.andReturn()
				.getResponse();

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}
}
