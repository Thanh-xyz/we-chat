package main.com.chat.wechat.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import main.com.chat.wechat.common.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
	private final RateLimiter rateLimiter;
	private final RateLimitProperties rateLimitProperties;
	private final ObjectMapper objectMapper;

	public RateLimitFilter(
			RateLimiter rateLimiter,
			RateLimitProperties rateLimitProperties,
			ObjectMapper objectMapper) {
		this.rateLimiter = rateLimiter;
		this.rateLimitProperties = rateLimitProperties;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		RateLimitProperties.Limit limit = limitFor(request);
		if (limit != null && !rateLimiter.tryConsume(bucketName(request), clientKey(request), limit)) {
			writeTooManyRequests(request, response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private RateLimitProperties.Limit limitFor(HttpServletRequest request) {
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			return null;
		}
		return switch (request.getRequestURI()) {
			case "/api/auth/login" -> rateLimitProperties.authLogin();
			case "/api/auth/register" -> rateLimitProperties.authRegister();
			case "/api/auth/refresh", "/api/auth/refresh-token" -> rateLimitProperties.authRefresh();
			default -> null;
		};
	}

	private String bucketName(HttpServletRequest request) {
		return request.getMethod() + ":" + request.getRequestURI();
	}

	private String clientKey(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}

	private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType("application/json");
		objectMapper.writeValue(response.getWriter(), new ErrorResponse(
				Instant.now(),
				HttpStatus.TOO_MANY_REQUESTS.value(),
				HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
				"Rate limit exceeded",
				request.getRequestURI(),
				null));
	}
}
