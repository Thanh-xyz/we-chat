package main.com.chat.wechat.conversation.service;

import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.dto.MarkConversationReadRequest;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationReadServiceTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
	private static final Instant MESSAGE_CREATED_AT = Instant.parse("2026-06-15T05:00:00Z");

	@Mock
	private ConversationService conversationService;

	@Mock
	private ConversationMemberRepository conversationMemberRepository;

	@Mock
	private MessageRepository messageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private RealtimeEventPublisher realtimeEventPublisher;

	private ConversationReadService conversationReadService;

	@BeforeEach
	void setUp() {
		conversationReadService = new ConversationReadService(
				conversationService,
				conversationMemberRepository,
				messageRepository,
				userRepository,
				realtimeEventPublisher);
	}

	@Test
	void markReadUsesLatestReadableMessageWhenRequestHasNoMessageId() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		when(conversationService.findAccessibleConversation(USER_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(messageRepository.findLatestMessageId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(MESSAGE_ID));
		when(messageRepository.findCreatedAt(MESSAGE_ID)).thenReturn(Optional.of(MESSAGE_CREATED_AT));
		when(conversationMemberRepository.countUnread(CONVERSATION_ID, USER_ID)).thenReturn(0);
		when(conversationMemberRepository.countTotalUnread(USER_ID, false)).thenReturn(0);
		when(conversationService.memberIds(CONVERSATION_ID)).thenReturn(List.of(USER_ID));

		var response = conversationReadService.markRead(USER_ID, CONVERSATION_ID, null);

		assertThat(response.lastReadMessageId()).isEqualTo(MESSAGE_ID);
		assertThat(response.lastReadAt()).isEqualTo(MESSAGE_CREATED_AT);
		assertThat(response.unreadCount()).isZero();
		verify(conversationMemberRepository).updateLastRead(eq(CONVERSATION_ID), eq(USER_ID), eq(MESSAGE_ID), eq(MESSAGE_CREATED_AT), any(Instant.class));
	}

	@Test
	void markReadRejectsUnreadableTargetMessage() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		when(conversationService.findAccessibleConversation(USER_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(messageRepository.existsReadableInConversation(MESSAGE_ID, CONVERSATION_ID, USER_ID)).thenReturn(false);

		assertThatThrownBy(() -> conversationReadService.markRead(USER_ID, CONVERSATION_ID, new MarkConversationReadRequest(MESSAGE_ID)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));

		verifyNoInteractions(realtimeEventPublisher);
	}

	private Conversation conversation() {
		Instant now = Instant.now();
		return new Conversation(CONVERSATION_ID, "DIRECT", null, null, USER_ID, MESSAGE_ID, now, null, now, now);
	}

	private User activeUser() {
		Instant now = Instant.now();
		return new User(USER_ID, "user", "user@example.com", "hash", "User", null, "ACTIVE", "USER", true, "ACTIVE", true, 0, null, 0, null, null, now, now);
	}
}
