package main.com.chat.wechat.audit.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextProviderTest {
	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
		SecurityContextHolder.clearContext();
	}

	@Test
	void auditContextUsesContainerRemoteAddressInsteadOfForwardingHeaders() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.10");
		request.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");
		request.addHeader("X-Real-IP", "3.3.3.3");
		request.addHeader("Forwarded", "for=4.4.4.4");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		RequestContextProvider.RequestContext context = new RequestContextProvider().current();

		assertThat(context.ipAddress()).isEqualTo("10.0.0.10");
	}
}
