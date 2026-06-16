package main.com.chat.wechat.conversation.service;

import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.dto.ConversationResponse;
import main.com.chat.wechat.conversation.dto.CreateConversationRequest;
import main.com.chat.wechat.conversation.dto.MuteConversationRequest;
import main.com.chat.wechat.conversation.dto.UpdateConversationRequest;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.model.ConversationMember;
import main.com.chat.wechat.conversation.model.DirectConversationPair;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.friendship.repository.FriendshipRepository;
import main.com.chat.wechat.friendship.repository.UserBlockRepository;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.notification.event.NotificationEventPublisher;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
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
	private MessageRepository messageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FriendshipRepository friendshipRepository;

	@Mock
	private UserBlockRepository userBlockRepository;

	@Mock
	private AuditLogService auditLogService;

	@Mock
	private AuditJsonWriter auditJsonWriter;

	@Mock
	private RealtimeEventPublisher realtimeEventPublisher;

	@Mock
	private NotificationEventPublisher notificationEventPublisher;

	private ConversationService conversationService;

	@BeforeEach
	void setUp() {
		conversationService = new ConversationService(
				conversationRepository,
				conversationMemberRepository,
				messageRepository,
				userRepository,
				friendshipRepository,
				userBlockRepository,
				auditLogService,
				auditJsonWriter,
				realtimeEventPublisher,
				notificationEventPublisher);
		lenient().when(messageRepository.countUnreadByConversationIds(any(UUID.class), any())).thenReturn(Map.of());
		lenient().when(auditJsonWriter.write(any())).thenReturn("{}");
		lenient().when(friendshipRepository.existsActive(any(UUID.class), any(UUID.class))).thenReturn(true);
		lenient().when(userBlockRepository.existsBlockBetween(any(UUID.class), any(UUID.class))).thenReturn(false);
		lenient().when(conversationMemberRepository.findActiveMember(any(UUID.class), any(UUID.class)))
				.thenReturn(Optional.empty());
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
		when(messageRepository.countUnreadByConversationIds(eq(USER_LOW), any())).thenReturn(Map.of());

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
		when(messageRepository.countUnreadByConversationIds(eq(USER_HIGH), any())).thenReturn(Map.of());

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
		when(messageRepository.countUnreadByConversationIds(eq(USER_LOW), any())).thenReturn(Map.of());

		ConversationResponse response = conversationService.create(USER_LOW, directRequest(USER_HIGH));

		assertThat(response.id()).isEqualTo(existingConversation.id());
		assertThat(response.memberIds()).containsExactly(USER_LOW, USER_HIGH);
		verify(conversationRepository, never()).save(any(Conversation.class));
		verify(conversationRepository, never()).saveDirectConversation(any(UUID.class), any(UUID.class), any(UUID.class));
	}

	@Test
	void updateGroupConversationAllowsOwner() {
		Conversation conversation = groupConversation(USER_LOW);
		Conversation updatedConversation = new Conversation(
				conversation.id(),
				"GROUP",
				"Updated",
				"https://cdn.example.com/avatar.png",
				USER_LOW,
				null,
				null,
				null,
				conversation.createdAt(),
				Instant.now());
		when(conversationRepository.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(conversationMemberRepository.isMember(conversation.id(), USER_LOW)).thenReturn(true);
		when(conversationMemberRepository.findActiveMember(conversation.id(), USER_LOW))
				.thenReturn(Optional.of(member(conversation.id(), USER_LOW, "OWNER")));
		when(conversationRepository.updateGroup(eq(conversation.id()), eq("Updated"), eq("https://cdn.example.com/avatar.png"), any(Instant.class)))
				.thenReturn(Optional.of(updatedConversation));
		when(conversationMemberRepository.findMemberIds(conversation.id())).thenReturn(List.of(USER_LOW, USER_HIGH));
		when(messageRepository.countUnreadByConversationIds(eq(USER_LOW), any())).thenReturn(Map.of());

		ConversationResponse response = conversationService.updateGroup(
				USER_LOW,
				conversation.id(),
				new UpdateConversationRequest("Updated", "https://cdn.example.com/avatar.png"));

		assertThat(response.name()).isEqualTo("Updated");
		assertThat(response.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
	}

	@Test
	void updateGroupConversationRejectsPlainMember() {
		Conversation conversation = groupConversation(USER_LOW);
		when(conversationRepository.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(conversationMemberRepository.isMember(conversation.id(), USER_HIGH)).thenReturn(true);
		when(conversationMemberRepository.findActiveMember(conversation.id(), USER_HIGH))
				.thenReturn(Optional.of(member(conversation.id(), USER_HIGH, "MEMBER")));

		assertThatThrownBy(() -> conversationService.updateGroup(
				USER_HIGH,
				conversation.id(),
				new UpdateConversationRequest("Updated", null)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));

		verify(conversationRepository, never()).updateGroup(any(UUID.class), any(), any(), any(Instant.class));
	}

	@Test
	void pinConversationStoresPinnedAtForCurrentUserOnly() {
		Conversation conversation = groupConversation(USER_LOW);
		ConversationMember pinnedMember = member(conversation.id(), USER_LOW, "MEMBER", Instant.now(), null, null);
		when(conversationRepository.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(conversationMemberRepository.isMember(conversation.id(), USER_LOW)).thenReturn(true);
		when(conversationMemberRepository.updatePinnedAt(eq(conversation.id()), eq(USER_LOW), any(Instant.class)))
				.thenReturn(pinnedMember);
		when(conversationMemberRepository.findMemberIds(conversation.id())).thenReturn(List.of(USER_LOW, USER_HIGH));
		when(messageRepository.countUnreadByConversationIds(eq(USER_LOW), any())).thenReturn(Map.of());

		ConversationResponse response = conversationService.pin(USER_LOW, conversation.id());

		assertThat(response.pinnedAt()).isNotNull();
		verify(conversationMemberRepository).updatePinnedAt(eq(conversation.id()), eq(USER_LOW), any(Instant.class));
	}

	@Test
	void muteConversationCanBeIndefinite() {
		Conversation conversation = groupConversation(USER_LOW);
		ConversationMember mutedMember = member(conversation.id(), USER_LOW, "MEMBER", null, null, null);
		mutedMember = new ConversationMember(
				mutedMember.conversationId(),
				mutedMember.userId(),
				mutedMember.memberRole(),
				mutedMember.nickname(),
				mutedMember.joinedAt(),
				mutedMember.leftAt(),
				null,
				mutedMember.pinnedAt(),
				mutedMember.archivedAt(),
				mutedMember.lastReadMessageId(),
				mutedMember.readAt(),
				true);
		when(conversationRepository.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(conversationMemberRepository.isMember(conversation.id(), USER_LOW)).thenReturn(true);
		when(conversationMemberRepository.updateMute(conversation.id(), USER_LOW, true, null)).thenReturn(mutedMember);
		when(conversationMemberRepository.findMemberIds(conversation.id())).thenReturn(List.of(USER_LOW, USER_HIGH));
		when(messageRepository.countUnreadByConversationIds(eq(USER_LOW), any())).thenReturn(Map.of());

		ConversationResponse response = conversationService.mute(USER_LOW, conversation.id(), new MuteConversationRequest(null));

		assertThat(response.muted()).isTrue();
		assertThat(response.mutedUntil()).isNull();
	}

	@Test
	void listConversationsDoesNotIncludeArchivedByDefault() {
		when(conversationRepository.findByMember(USER_LOW, false, 50, 0)).thenReturn(List.of());
		when(conversationMemberRepository.findMemberIdsByConversationIds(List.of())).thenReturn(Map.of());
		when(messageRepository.countUnreadByConversationIds(USER_LOW, List.of())).thenReturn(Map.of());
		when(conversationMemberRepository.findActiveMembersByConversationIds(USER_LOW, List.of())).thenReturn(Map.of());

		List<ConversationResponse> responses = conversationService.list(USER_LOW, false, 50, 0);

		assertThat(responses).isEmpty();
		verify(conversationRepository).findByMember(USER_LOW, false, 50, 0);
		verifyNoInteractions(realtimeEventPublisher);
	}

	@Test
	void createDirectConversationReturnsExistingConversationWhenRaceConflictHappens() {
		Conversation temporaryConversation = conversation(USER_LOW);
		Conversation existingConversation = conversation(USER_LOW);
		givenActiveUsers(USER_LOW, USER_HIGH);
		when(conversationRepository.findDirectByPair(USER_LOW, USER_HIGH))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(existingConversation));
		when(conversationRepository.save(any(Conversation.class))).thenReturn(temporaryConversation);
		when(conversationRepository.saveDirectConversation(temporaryConversation.id(), USER_LOW, USER_HIGH)).thenReturn(false);
		when(conversationMemberRepository.findMemberIds(existingConversation.id())).thenReturn(List.of(USER_LOW, USER_HIGH));
		when(messageRepository.countUnreadByConversationIds(eq(USER_LOW), any())).thenReturn(Map.of());

		ConversationResponse response = conversationService.create(USER_LOW, directRequest(USER_HIGH));

		assertThat(response.id()).isEqualTo(existingConversation.id());
		verify(conversationRepository).deleteById(temporaryConversation.id());
		verify(conversationMemberRepository, never()).save(any(ConversationMember.class));
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

	private Conversation groupConversation(UUID createdBy) {
		Instant now = Instant.now();
		return new Conversation(
				UUID.randomUUID(),
				"GROUP",
				"Group",
				null,
				createdBy,
				null,
				null,
				null,
				now,
				now);
	}

	private ConversationMember member(UUID conversationId, UUID userId, String role) {
		return member(conversationId, userId, role, null, null, null);
	}

	private ConversationMember member(
			UUID conversationId,
			UUID userId,
			String role,
			Instant pinnedAt,
			Instant mutedUntil,
			Instant archivedAt) {
		Instant now = Instant.now();
		return new ConversationMember(
				conversationId,
				userId,
				role,
				null,
					now,
					null,
					mutedUntil,
					pinnedAt,
					archivedAt,
				null,
				null,
				false);
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
