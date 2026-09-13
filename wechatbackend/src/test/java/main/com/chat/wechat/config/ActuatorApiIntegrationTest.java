package main.com.chat.wechat.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:actuator_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.locations=classpath:db/test-auth-migration",
		"app.jwt.secret=test-secret-test-secret-test-secret-1234",
		"app.jwt.issuer=wechat-test",
		"app.jwt.access-token-ttl=PT15M",
		"app.jwt.refresh-token-ttl=P30D"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorApiIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthIsPublicAndDoesNotExposeDependencyDetails() throws Exception {
		var response = mockMvc.perform(get("/actuator/health"))
				.andReturn()
				.getResponse();
		String body = response.getContentAsString();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentType()).contains("json");
		assertThat(body).contains("\"status\":\"UP\"");
		assertThat(body).doesNotContain("jdbc:h2", "username", "password", "stacktrace");
	}

	@Test
	void livenessAndReadinessAreAvailableWithoutJwt() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200))
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"UP\""));

		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200))
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"UP\""));
	}

	@Test
	void prometheusIsPubliclyScrapableAndContainsJvmAndHttpMetrics() throws Exception {
		mockMvc.perform(get("/actuator/health"));

		var response = mockMvc.perform(get("/actuator/prometheus"))
				.andReturn()
				.getResponse();
		String body = response.getContentAsString();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentType()).contains("text/plain");
		assertThat(body).contains("jvm_memory_used_bytes", "process_cpu_usage", "http_server_requests_seconds");
		assertThat(body).doesNotContain("JWT_SECRET", "MAIL_PASSWORD", "DATABASE_PASSWORD", "Authorization:", "Bearer ");
	}

	@Test
	void unapprovedActuatorEndpointsAreNotPubliclyAvailable() throws Exception {
		for (String endpoint : new String[] {
				"/actuator/env",
				"/actuator/configprops",
				"/actuator/beans",
				"/actuator/threaddump",
				"/actuator/heapdump"}) {
			var response = mockMvc.perform(get(endpoint)).andReturn().getResponse();
			assertThat(response.getStatus()).as(endpoint).isIn(401, 403, 404);
			assertThat(response.getContentAsString()).as(endpoint)
				.doesNotContain("DATABASE_PASSWORD", "MAIL_PASSWORD", "JWT_SECRET");
		}
	}

	@Test
	void httpMetricsUseLowCardinalityUriValues() throws Exception {
		String uuidA = "7d5c0e62-7348-46e0-9a2d-6cbb3ed7d4a1";
		String uuidB = "fe4f8ca5-95b4-4514-90b1-6b2ddf47ca1c";

		mockMvc.perform(get("/api/observability/" + uuidA));
		mockMvc.perform(get("/api/observability/" + uuidB));

		String body = mockMvc.perform(get("/actuator/prometheus"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(body).contains("http_server_requests_seconds");
		assertThat(body).doesNotContain(uuidA, uuidB, "user_id=", "conversation_id=", "message_id=");
	}
}
