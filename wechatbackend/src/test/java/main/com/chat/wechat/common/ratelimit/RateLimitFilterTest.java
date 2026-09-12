package main.com.chat.wechat.common.ratelimit;

import org.apache.catalina.filters.RemoteIpFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {
	@Test
	void loginReturnsTooManyRequestsWhenCapacityExceeded() throws Exception {
		RateLimitFilter filter = filter(new InMemoryRateLimiter(), 1);

		MockHttpServletResponse firstResponse = apply(filter, loginRequest("127.0.0.1", null));
		MockHttpServletResponse secondResponse = apply(filter, loginRequest("127.0.0.1", null));

		assertThat(firstResponse.getStatus()).isEqualTo(200);
		assertThat(secondResponse.getStatus()).isEqualTo(429);
		assertThat(secondResponse.getContentAsString()).contains("Rate limit exceeded");
	}

	@Test
	void directClientUsesConnectionSourceAndIgnoresEveryForwardingHeader() throws Exception {
		MockHttpServletRequest request = loginRequest("10.0.0.10", "1.1.1.1");
		request.addHeader("X-Real-IP", "2.2.2.2");
		request.addHeader("Forwarded", "for=3.3.3.3");

		assertThat(capturedClientKey(request)).isEqualTo("10.0.0.10");
		assertThat(capturedClientKey(loginRequest("2001:db8::10", "2001:db8::99")))
				.isEqualTo("2001:db8::10");
	}

	@Test
	void changingSpoofedHeadersCannotBypassActualLoginRateLimit() throws Exception {
		RateLimitFilter filter = filter(new InMemoryRateLimiter(), 2);

		MockHttpServletResponse first = apply(filter, loginRequest("10.0.0.10", "1.1.1.1"));
		MockHttpServletResponse second = apply(filter, loginRequest("10.0.0.10", "2.2.2.2"));
		MockHttpServletResponse third = apply(filter, loginRequest("10.0.0.10", "3.3.3.3"));

		assertThat(first.getStatus()).isEqualTo(200);
		assertThat(second.getStatus()).isEqualTo(200);
		assertThat(third.getStatus()).isEqualTo(429);
	}

	@Test
	void emptyTrustedProxyConfigurationTrustsNoProxy() throws Exception {
		assertThat(clientKeyThroughNativeProxy("10.0.0.20", "8.8.8.8", ""))
				.isEqualTo("10.0.0.20");
	}

	@Test
	void trustedProxyCanSupplyClientAddress() throws Exception {
		assertThat(clientKeyThroughNativeProxy("10.0.0.20", "8.8.8.8", "10.0.0.0/8"))
				.isEqualTo("8.8.8.8");
		assertThat(clientKeyThroughNativeProxy("fd00::20", "2001:4860:4860::8888", "fd00::/8"))
				.isEqualTo("2001:4860:4860::8888");
	}

	@Test
	void untrustedProxyCannotSupplyClientAddress() throws Exception {
		assertThat(clientKeyThroughNativeProxy("192.0.2.20", "8.8.8.8", "10.0.0.0/8"))
				.isEqualTo("192.0.2.20");
	}

	@Test
	void nativeProxyResolutionWalksMultipleEntriesFromTrustedEdge() throws Exception {
		assertThat(clientKeyThroughNativeProxy(
				"10.0.0.20",
				"198.51.100.7, 10.0.0.2, 10.0.0.3",
				"10.0.0.0/8"))
				.isEqualTo("198.51.100.7");
		assertThat(clientKeyThroughNativeProxy(
				"10.0.0.20",
				"1.1.1.1, 198.51.100.7, 10.0.0.3",
				"10.0.0.0/8"))
				.isEqualTo("198.51.100.7");
	}

	@Test
	void malformedForwardingHeadersDoNotCrashOrCreateNewBucketsForDirectClient() throws Exception {
		RateLimitFilter filter = filter(new InMemoryRateLimiter(), 2);

		MockHttpServletResponse first = apply(filter, loginRequest("127.0.0.1", "garbage"));
		MockHttpServletResponse second = apply(filter, loginRequest("127.0.0.1", ""));
		MockHttpServletResponse third = apply(filter, loginRequest("127.0.0.1", "999.999.999.999"));

		assertThat(first.getStatus()).isEqualTo(200);
		assertThat(second.getStatus()).isEqualTo(200);
		assertThat(third.getStatus()).isEqualTo(429);
	}

	private String capturedClientKey(MockHttpServletRequest request) throws Exception {
		AtomicReference<String> clientKey = new AtomicReference<>();
		RateLimiter capturingLimiter = (bucketName, key, limit) -> {
			clientKey.set(key);
			return true;
		};
		apply(filter(capturingLimiter, 2), request);
		return clientKey.get();
	}

	private String clientKeyThroughNativeProxy(
			String proxyAddress,
			String forwardedFor,
			String internalProxies) throws Exception {
		AtomicReference<String> clientKey = new AtomicReference<>();
		RateLimiter capturingLimiter = (bucketName, key, limit) -> {
			clientKey.set(key);
			return true;
		};
		RateLimitFilter rateLimitFilter = filter(capturingLimiter, 2);
		RemoteIpFilter remoteIpFilter = new RemoteIpFilter();
		remoteIpFilter.setRemoteIpHeader("X-Forwarded-For");
		remoteIpFilter.setInternalProxies(internalProxies);
		remoteIpFilter.setTrustedProxies("");
		MockHttpServletRequest request = loginRequest(proxyAddress, forwardedFor);
		MockHttpServletResponse response = new MockHttpServletResponse();

		remoteIpFilter.doFilter(
				request,
				response,
				(servletRequest, servletResponse) -> rateLimitFilter.doFilter(
						servletRequest,
						servletResponse,
						new MockFilterChain()));
		return clientKey.get();
	}

	private RateLimitFilter filter(RateLimiter rateLimiter, int loginCapacity) {
		RateLimitProperties properties = new RateLimitProperties(
				new RateLimitProperties.Limit(loginCapacity, 1),
				new RateLimitProperties.Limit(20, 1),
				new RateLimitProperties.Limit(5, 1),
				new RateLimitProperties.Limit(3, 15),
				new RateLimitProperties.Limit(60, 1),
				new RateLimitProperties.Limit(20, 1));
		return new RateLimitFilter(rateLimiter, properties, new ObjectMapper());
	}

	private MockHttpServletResponse apply(RateLimitFilter filter, MockHttpServletRequest request) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

	private MockHttpServletRequest loginRequest(String remoteAddress, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.setRemoteAddr(remoteAddress);
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor);
		}
		return request;
	}
}
