package main.com.chat.wechat.conversation.service;

import main.com.chat.wechat.conversation.dto.TypingEventRequest;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypingServiceTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

	@Mock
	private ConversationService conversationService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private RealtimeEventPublisher realtimeEventPublisher;

	private TypingService typingService;

	@BeforeEach
	void setUp() {
		typingService = new TypingService(conversationService, userRepository, realtimeEventPublisher);
	}

	@Test
	void publishTypingSendsEventToOtherMembersOnlyAndDebouncesRepeats() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		when(conversationService.findAccessibleConversation(USER_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(conversationService.memberIds(CONVERSATION_ID)).thenReturn(List.of(USER_ID, OTHER_USER_ID));

		var first = typingService.publishTyping(USER_ID, CONVERSATION_ID, new TypingEventRequest(true));
		var second = typingService.publishTyping(USER_ID, CONVERSATION_ID, new TypingEventRequest(true));

		assertThat(first.published()).isTrue();
		assertThat(second.published()).isFalse();
		verify(realtimeEventPublisher).publishToMembersAfterCommit(eq(List.of(OTHER_USER_ID)), any());
	}

	@Test
	void stoppedTypingPublishesAfterStartedState() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		when(conversationService.findAccessibleConversation(USER_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(conversationService.memberIds(CONVERSATION_ID)).thenReturn(List.of(USER_ID, OTHER_USER_ID));

		typingService.publishTyping(USER_ID, CONVERSATION_ID, new TypingEventRequest(true));
		var stopped = typingService.publishTyping(USER_ID, CONVERSATION_ID, new TypingEventRequest(false));

		assertThat(stopped.eventType()).isEqualTo("conversation.typing.stopped");
		assertThat(stopped.published()).isTrue();
		verify(realtimeEventPublisher, never()).publishToMembersAfterCommit(eq(List.of(USER_ID)), any());
	}

	private Conversation conversation() {
		Instant now = Instant.now();
		return new Conversation(CONVERSATION_ID, "DIRECT", null, null, USER_ID, null, null, null, now, now);
	}

	private User activeUser() {
		Instant now = Instant.now();
		return new User(USER_ID, "user", "user@example.com", "hash", "User", null, "ACTIVE", "USER", true, "ACTIVE", true, 0, null, 0, null, null, now, now);
	}
}
