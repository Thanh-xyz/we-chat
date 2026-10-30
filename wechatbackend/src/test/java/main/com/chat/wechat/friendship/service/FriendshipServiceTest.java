package main.com.chat.wechat.friendship.service;

import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.friendship.dto.SendFriendRequestRequest;
import main.com.chat.wechat.friendship.model.FriendRequest;
import main.com.chat.wechat.friendship.repository.FriendRequestRepository;
import main.com.chat.wechat.friendship.repository.FriendshipRepository;
import main.com.chat.wechat.friendship.repository.UserBlockRepository;
import main.com.chat.wechat.notification.service.NotificationService;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {
	private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID RECEIVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Mock private FriendRequestRepository friendRequestRepository;
	@Mock private FriendshipRepository friendshipRepository;
	@Mock private UserBlockRepository userBlockRepository;
	@Mock private UserRepository userRepository;
	@Mock private ConversationRepository conversationRepository;
	@Mock private ConversationMemberRepository conversationMemberRepository;
	@Mock private RealtimeEventPublisher realtimeEventPublisher;
	@Mock private NotificationService notificationService;
	@Mock private AuditLogService auditLogService;
	@Mock private AuditJsonWriter auditJsonWriter;

	private FriendshipService friendshipService;

	@BeforeEach
	void setUp() {
		friendshipService = new FriendshipService(
				friendRequestRepository,
				friendshipRepository,
				userBlockRepository,
				userRepository,
				conversationRepository,
				conversationMemberRepository,
				realtimeEventPublisher,
				notificationService,
				auditLogService,
				auditJsonWriter);
	}

	@Test
	void sendRequestExpiresStalePendingPairBeforeCreatingReplacement() {
		User actor = activeUser(ACTOR_ID, "Actor");
		User receiver = activeUser(RECEIVER_ID, "Receiver");
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
		when(userRepository.findById(RECEIVER_ID)).thenReturn(Optional.of(receiver));
		when(friendshipRepository.existsActive(ACTOR_ID, RECEIVER_ID)).thenReturn(false);
		when(userBlockRepository.existsBlockBetween(ACTOR_ID, RECEIVER_ID)).thenReturn(false);
		when(friendRequestRepository.findPendingBetween(ACTOR_ID, RECEIVER_ID)).thenReturn(Optional.empty());
		when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var response = friendshipService.sendRequest(ACTOR_ID, new SendFriendRequestRequest(RECEIVER_ID, "Xin chào"));

		verify(friendRequestRepository).expirePendingBetween(eq(ACTOR_ID), eq(RECEIVER_ID), any(Instant.class));
		assertThat(response.requester().userId()).isEqualTo(ACTOR_ID);
		assertThat(response.receiver().userId()).isEqualTo(RECEIVER_ID);
	}

	private User activeUser(UUID id, String displayName) {
		Instant now = Instant.now();
		return new User(
				id, "user-" + id, id + "@example.com", "hash", displayName,
				null, "ONLINE", "USER", true, "ACTIVE", true, 0,
				null, 0, null, null, now, now);
	}
}
