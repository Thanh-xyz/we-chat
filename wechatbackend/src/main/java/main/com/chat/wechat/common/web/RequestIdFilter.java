package main.com.chat.wechat.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import main.com.chat.wechat.audit.service.RequestContextProvider;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
	private static final int MAX_ID_LENGTH = 128;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String requestId = headerOrGenerated(request, RequestContextProvider.REQUEST_ID_HEADER);
		String traceId = traceId(request, requestId);
		request.setAttribute(RequestContextProvider.REQUEST_ID_ATTRIBUTE, requestId);
		request.setAttribute(RequestContextProvider.TRACE_ID_ATTRIBUTE, traceId);
		response.setHeader(RequestContextProvider.REQUEST_ID_HEADER, requestId);
		MDC.put(RequestContextProvider.MDC_REQUEST_ID, requestId);
		MDC.put(RequestContextProvider.MDC_TRACE_ID, traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(RequestContextProvider.MDC_REQUEST_ID);
			MDC.remove(RequestContextProvider.MDC_TRACE_ID);
		}
	}

	private String headerOrGenerated(HttpServletRequest request, String headerName) {
		String header = request.getHeader(headerName);
		if (!StringUtils.hasText(header)) {
			return UUID.randomUUID().toString();
		}
		return sanitizeId(header);
	}

	private String traceId(HttpServletRequest request, String fallback) {
		String traceParent = request.getHeader("traceparent");
		if (StringUtils.hasText(traceParent)) {
			String[] parts = traceParent.split("-");
			if (parts.length >= 2 && StringUtils.hasText(parts[1])) {
				return sanitizeId(parts[1]);
			}
		}
		String b3TraceId = request.getHeader("X-B3-TraceId");
		return StringUtils.hasText(b3TraceId) ? sanitizeId(b3TraceId) : fallback;
	}

	private String sanitizeId(String value) {
		String sanitized = value.trim().replaceAll("[^A-Za-z0-9._:-]", "");
		if (sanitized.length() > MAX_ID_LENGTH) {
			return sanitized.substring(0, MAX_ID_LENGTH);
		}
		return StringUtils.hasText(sanitized) ? sanitized : UUID.randomUUID().toString();
	}
}
