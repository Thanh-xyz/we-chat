package main.com.chat.wechat.conversation.service;

import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.dto.ConversationResponse;
import main.com.chat.wechat.conversation.dto.CreateConversationRequest;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.model.ConversationMember;
import main.com.chat.wechat.conversation.model.DirectConversationPair;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
	private static final UUID USER_LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID USER_HIGH = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private ConversationMemberRepository conversationMemberRepository;

	@Mock
	private UserRepository userRepository;

	private ConversationService conversationService;

	@BeforeEach
	void setUp() {
		conversationService = new ConversationService(conversationRepository, conversationMemberRepository, userRepository);
	}

	@Test
	void createDirectConversationKeepsPairOrderWhenActorIsLowerUuid() {
		givenActiveUsers(USER_LOW, USER_HIGH);
		givenNewConversationCanBeSaved();
		when(conversationRepository.findDirectByPair(USER_LOW, USER_HIGH)).thenReturn(Optional.empty());
		when(conversationRepository.saveDirectConversation(any(UUID.class), eq(USER_LOW), eq(USER_HIGH))).thenReturn(true);
		when(conversationMemberRepository.save(any(ConversationMember.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(conversationMemberRepository.findMemberIds(any(UUID.class))).thenReturn(List.of(USER_LOW, USER_HIGH));

		conversationService.create(USER_LOW, directRequest(USER_HIGH));

		verify(conversationRepository).saveDirectConversation(any(UUID.class), eq(USER_LOW), eq(USER_HIGH));
	}

	@Test
	void createDirectConversationSortsPairWhenActorIsHigherUuid() {
		givenActiveUsers(USER_HIGH, USER_LOW);
		givenNewConversationCanBeSaved();
		when(conversationRepository.findDirectByPair(USER_LOW, USER_HIGH)).thenReturn(Optional.empty());
		when(conversationRepository.saveDirectConversation(any(UUID.class), eq(USER_LOW), eq(USER_HIGH))).thenReturn(true);
		when(conversationMemberRepository.save(any(ConversationMember.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(conversationMemberRepository.findMemberIds(any(UUID.class))).thenReturn(List.of(USER_LOW, USER_HIGH));

		conversationService.create(USER_HIGH, directRequest(USER_LOW));

		verify(conversationRepository).saveDirectConversation(any(UUID.class), eq(USER_LOW), eq(USER_HIGH));
	}

	@Test
	void createDirectConversationRejectsSelfConversation() {
		when(userRepository.findById(USER_LOW)).thenReturn(Optional.of(activeUser(USER_LOW)));

		assertThatThrownBy(() -> conversationService.create(USER_LOW, directRequest(USER_LOW)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getMessage()).isEqualTo(DirectConversationPair.SELF_CONVERSATION_MESSAGE);
				});

		verify(conversationRepository, never()).save(any(Conversation.class));
		verify(conversationRepository, never()).saveDirectConversation(any(UUID.class), any(UUID.class), any(UUID.class));
	}

	@Test
	void createDirectConversationReturnsExistingConversationWhenPairAlreadyExists() {
		Conversation existingConversation = conversation(USER_LOW);
		givenActiveUsers(USER_LOW, USER_HIGH);
		when(conversationRepository.findDirectByPair(USER_LOW, USER_HIGH)).thenReturn(Optional.of(existingConversation));
		when(conversationMemberRepository.findMemberIds(existingConversation.id())).thenReturn(List.of(USER_LOW, USER_HIGH));

		ConversationResponse response = conversationService.create(USER_LOW, directRequest(USER_HIGH));

		assertThat(response.id()).isEqualTo(existingConversation.id());
		assertThat(response.memberIds()).containsExactly(USER_LOW, USER_HIGH);
		verify(conversationRepository, never()).save(any(Conversation.class));
		verify(conversationRepository, never()).saveDirectConversation(any(UUID.class), any(UUID.class), any(UUID.class));
	}

	private void givenActiveUsers(UUID firstUserId, UUID secondUserId) {
		when(userRepository.findById(firstUserId)).thenReturn(Optional.of(activeUser(firstUserId)));
		when(userRepository.findById(secondUserId)).thenReturn(Optional.of(activeUser(secondUserId)));
	}

	private void givenNewConversationCanBeSaved() {
		when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private CreateConversationRequest directRequest(UUID otherUserId) {
		return new CreateConversationRequest("DIRECT", null, null, Set.of(otherUserId));
	}

	private Conversation conversation(UUID createdBy) {
		Instant now = Instant.now();
		return new Conversation(
				UUID.randomUUID(),
				"DIRECT",
				null,
				null,
				createdBy,
				null,
				null,
				null,
				now,
				now);
	}

	private User activeUser(UUID id) {
		Instant now = Instant.now();
		return new User(
				id,
				"user-" + id,
				id + "@example.com",
				"hash",
				"User",
				null,
				"ONLINE",
				"USER",
				true,
				"ACTIVE",
				true,
				0,
				null,
				0,
				null,
				null,
				now,
				now);
	}
}
