package main.com.chat.wechat.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {
	@Test
	void loginReturnsTooManyRequestsWhenCapacityExceeded() throws Exception {
		RateLimitProperties properties = new RateLimitProperties(
				new RateLimitProperties.Limit(1, 1),
				new RateLimitProperties.Limit(20, 1),
				new RateLimitProperties.Limit(5, 1),
				new RateLimitProperties.Limit(3, 15),
				new RateLimitProperties.Limit(60, 1),
				new RateLimitProperties.Limit(20, 1));
		RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimiter(), properties, new ObjectMapper());

		MockHttpServletResponse firstResponse = new MockHttpServletResponse();
		filter.doFilter(loginRequest(), firstResponse, new MockFilterChain());
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		filter.doFilter(loginRequest(), secondResponse, new MockFilterChain());

		assertThat(firstResponse.getStatus()).isEqualTo(200);
		assertThat(secondResponse.getStatus()).isEqualTo(429);
		assertThat(secondResponse.getContentAsString()).contains("Rate limit exceeded");
	}

	private MockHttpServletRequest loginRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.setRemoteAddr("127.0.0.1");
		return request;
	}
}
