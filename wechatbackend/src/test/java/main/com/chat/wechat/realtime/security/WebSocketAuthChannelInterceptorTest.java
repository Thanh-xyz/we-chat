package main.com.chat.wechat.realtime.security;

import main.com.chat.wechat.common.ratelimit.RateLimitProperties;
import main.com.chat.wechat.common.ratelimit.RateLimiter;
import main.com.chat.wechat.common.security.JwtTokenService;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

	@Mock
	private JwtTokenService jwtTokenService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ConversationMemberRepository conversationMemberRepository;

	@Mock
	private RateLimiter rateLimiter;

	private WebSocketAuthChannelInterceptor interceptor;

	@BeforeEach
	void setUp() {
		RateLimitProperties rateLimitProperties = new RateLimitProperties(
				new RateLimitProperties.Limit(5, 1),
				new RateLimitProperties.Limit(20, 1),
				new RateLimitProperties.Limit(5, 1),
				new RateLimitProperties.Limit(3, 15),
				new RateLimitProperties.Limit(60, 1),
				new RateLimitProperties.Limit(20, 1));
		interceptor = new WebSocketAuthChannelInterceptor(
				jwtTokenService,
				userRepository,
				conversationMemberRepository,
				rateLimiter,
				rateLimitProperties);
	}

	@Test
	void subscribeRejectsUserWhoIsNotConversationMember() {
		Message<byte[]> message = stompMessage(
				StompCommand.SUBSCRIBE,
				"/topic/conversations/" + CONVERSATION_ID,
				USER_ID);
		when(conversationMemberRepository.isMember(CONVERSATION_ID, USER_ID)).thenReturn(false);

		assertThatThrownBy(() -> interceptor.preSend(message, null))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("User is not a member of this conversation");
	}

	@Test
	void subscribeAllowsConversationMember() {
		Message<byte[]> message = stompMessage(
				StompCommand.SUBSCRIBE,
				"/topic/conversations/" + CONVERSATION_ID,
				USER_ID);
		when(conversationMemberRepository.isMember(CONVERSATION_ID, USER_ID)).thenReturn(true);

		Message<?> response = interceptor.preSend(message, null);

		assertThat(response).isSameAs(message);
	}

	@Test
	void sendRejectsUserWhoIsNotConversationMember() {
		Message<byte[]> message = stompMessage(
				StompCommand.SEND,
				"/app/conversations/" + CONVERSATION_ID + "/messages",
				USER_ID);
		when(rateLimiter.tryConsume("ws-message-send", USER_ID.toString(), new RateLimitProperties.Limit(60, 1)))
				.thenReturn(true);
		when(conversationMemberRepository.isMember(CONVERSATION_ID, USER_ID)).thenReturn(false);

		assertThatThrownBy(() -> interceptor.preSend(message, null))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void sendRejectsWhenMessageRateLimitExceeded() {
		Message<byte[]> message = stompMessage(
				StompCommand.SEND,
				"/app/conversations/" + CONVERSATION_ID + "/messages",
				USER_ID);
		when(rateLimiter.tryConsume("ws-message-send", USER_ID.toString(), new RateLimitProperties.Limit(60, 1)))
				.thenReturn(false);

		assertThatThrownBy(() -> interceptor.preSend(message, null))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("WebSocket message rate limit exceeded");
	}

	private Message<byte[]> stompMessage(StompCommand command, String destination, UUID userId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setDestination(destination);
		accessor.setUser(new WebSocketUserPrincipal(userId, "user"));
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
