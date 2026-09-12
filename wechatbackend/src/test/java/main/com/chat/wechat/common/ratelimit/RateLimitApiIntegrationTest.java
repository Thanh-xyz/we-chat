package main.com.chat.wechat.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:rate_limit_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.locations=classpath:db/test-auth-migration",
		"app.jwt.secret=test-secret-test-secret-test-secret-1234",
		"app.jwt.issuer=wechat-test",
		"app.jwt.access-token-ttl=PT15M",
		"app.jwt.refresh-token-ttl=P30D",
		"app.rate-limit.auth-login.capacity=2",
		"app.rate-limit.auth-login.refill-minutes=60",
		"app.rate-limit.auth-register.capacity=200",
		"app.rate-limit.auth-refresh.capacity=200",
		"app.rate-limit.auth-resend-verification.capacity=200"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitApiIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void spoofedForwardedAddressesCannotBypassLoginRateLimit() throws Exception {
		login("1.1.1.1").andExpect(status().isUnauthorized());
		login("2.2.2.2").andExpect(status().isUnauthorized());
		login("3.3.3.3").andExpect(status().isTooManyRequests());
	}

	private ResultActions login(String forwardedFor) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.with(remoteAddress("10.0.0.10"))
				.header("X-Forwarded-For", forwardedFor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"identifier":"missing-user","password":"Password@123"}
						"""));
	}

	private RequestPostProcessor remoteAddress(String address) {
		return request -> {
			request.setRemoteAddr(address);
			return request;
		};
	}
}
