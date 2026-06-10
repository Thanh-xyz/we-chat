package main.com.chat.wechat.realtime.security;

import main.com.chat.wechat.common.security.JwtTokenService;
import main.com.chat.wechat.common.ratelimit.RateLimitProperties;
import main.com.chat.wechat.common.ratelimit.RateLimiter;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
	private static final Pattern CONVERSATION_DESTINATION_PATTERN = Pattern.compile(
			"^/(topic|queue|app)/conversations/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(/.*)?$");

	private final JwtTokenService jwtTokenService;
	private final UserRepository userRepository;
	private final ConversationMemberRepository conversationMemberRepository;
	private final RateLimiter rateLimiter;
	private final RateLimitProperties rateLimitProperties;

	public WebSocketAuthChannelInterceptor(
			JwtTokenService jwtTokenService,
			UserRepository userRepository,
			ConversationMemberRepository conversationMemberRepository,
			RateLimiter rateLimiter,
			RateLimitProperties rateLimitProperties) {
		this.jwtTokenService = jwtTokenService;
		this.userRepository = userRepository;
		this.conversationMemberRepository = conversationMemberRepository;
		this.rateLimiter = rateLimiter;
		this.rateLimitProperties = rateLimitProperties;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}
		if (accessor.getCommand() == StompCommand.CONNECT) {
			authenticateConnect(accessor);
			return message;
		}
		if (accessor.getCommand() == StompCommand.SUBSCRIBE || accessor.getCommand() == StompCommand.SEND) {
			authorizeConversationDestination(accessor);
		}
		return message;
	}

	private void authenticateConnect(StompHeaderAccessor accessor) {
		String token = resolveBearerToken(accessor);
		if (token == null) {
			throw new AccessDeniedException("WebSocket authentication required");
		}
		if (!rateLimiter.tryConsume("ws-connect", token, rateLimitProperties.websocketConnect())) {
			throw new AccessDeniedException("WebSocket connect rate limit exceeded");
		}
		var claims = jwtTokenService.validateAccessToken(token)
				.orElseThrow(() -> new AccessDeniedException("Invalid WebSocket token"));
		boolean currentUserValid = userRepository.findById(claims.userId())
				.filter(user -> user.active() && user.tokenVersion() == claims.tokenVersion())
				.isPresent();
		if (!currentUserValid) {
			throw new AccessDeniedException("Invalid WebSocket user");
		}
		accessor.setUser(new WebSocketUserPrincipal(claims.userId(), claims.username()));
	}

	private void authorizeConversationDestination(StompHeaderAccessor accessor) {
		UUID conversationId = conversationIdFromDestination(accessor.getDestination());
		if (conversationId == null) {
			return;
		}
		if (!(accessor.getUser() instanceof WebSocketUserPrincipal principal)) {
			throw new AccessDeniedException("WebSocket authentication required");
		}
		if (accessor.getCommand() == StompCommand.SEND
				&& !rateLimiter.tryConsume("ws-message-send", principal.userId().toString(), rateLimitProperties.messageSend())) {
			throw new AccessDeniedException("WebSocket message rate limit exceeded");
		}
		if (!conversationMemberRepository.isMember(conversationId, principal.userId())) {
			throw new AccessDeniedException("User is not a member of this conversation");
		}
	}

	private UUID conversationIdFromDestination(String destination) {
		if (!StringUtils.hasText(destination)) {
			return null;
		}
		Matcher matcher = CONVERSATION_DESTINATION_PATTERN.matcher(destination);
		if (!matcher.matches()) {
			return null;
		}
		return UUID.fromString(matcher.group(2));
	}

	private String resolveBearerToken(StompHeaderAccessor accessor) {
		String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
		if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
			return null;
		}
		return authorization.substring(7);
	}
}
