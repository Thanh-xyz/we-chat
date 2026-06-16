package main.com.chat.wechat.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class RequestContextProvider {
	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String REQUEST_ID_ATTRIBUTE = "requestId";
	public static final String TRACE_ID_ATTRIBUTE = "traceId";
	public static final String MDC_REQUEST_ID = "request_id";
	public static final String MDC_TRACE_ID = "trace_id";

	public RequestContext current() {
		AuthenticatedUser actor = currentActor();
		HttpServletRequest request = currentRequest();
		return new RequestContext(
				actor == null ? null : actor.id(),
				actor == null ? null : actor.username(),
				actor == null ? null : actor.email(),
				clientIp(request),
				request == null ? null : request.getHeader(HttpHeaders.USER_AGENT),
				valueFromRequestOrMdc(request, REQUEST_ID_ATTRIBUTE, MDC_REQUEST_ID),
				valueFromRequestOrMdc(request, TRACE_ID_ATTRIBUTE, MDC_TRACE_ID));
	}

	private AuthenticatedUser currentActor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			return null;
		}
		return user;
	}

	private HttpServletRequest currentRequest() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
			return servletRequestAttributes.getRequest();
		}
		return null;
	}

	private String clientIp(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwardedFor)) {
			return forwardedFor.split(",", 2)[0].trim();
		}
		String realIp = request.getHeader("X-Real-IP");
		return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
	}

	private String valueFromRequestOrMdc(HttpServletRequest request, String attributeName, String mdcName) {
		if (request != null && request.getAttribute(attributeName) instanceof String value && StringUtils.hasText(value)) {
			return value;
		}
		String mdcValue = MDC.get(mdcName);
		return StringUtils.hasText(mdcValue) ? mdcValue : null;
	}

	public record RequestContext(
			UUID actorUserId,
			String actorUsername,
			String actorEmail,
			String ipAddress,
			String userAgent,
			String requestId,
			String traceId) {
	}
}
