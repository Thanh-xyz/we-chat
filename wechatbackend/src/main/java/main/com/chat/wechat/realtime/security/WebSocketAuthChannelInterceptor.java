package main.com.chat.wechat.realtime.security;

import main.com.chat.wechat.common.security.JwtTokenService;
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

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
	private final JwtTokenService jwtTokenService;
	private final UserRepository userRepository;

	public WebSocketAuthChannelInterceptor(JwtTokenService jwtTokenService, UserRepository userRepository) {
		this.jwtTokenService = jwtTokenService;
		this.userRepository = userRepository;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
			return message;
		}
		String token = resolveBearerToken(accessor);
		if (token == null) {
			throw new AccessDeniedException("WebSocket authentication required");
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
		return message;
	}

	private String resolveBearerToken(StompHeaderAccessor accessor) {
		String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
		if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
			return null;
		}
		return authorization.substring(7);
	}
}
