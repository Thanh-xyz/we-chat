package main.com.chat.wechat.notification.service;

import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.notification.event.NotificationEvent;
import main.com.chat.wechat.notification.model.Notification;
import main.com.chat.wechat.notification.model.NotificationPreference;
import main.com.chat.wechat.notification.repository.NotificationRepository;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
	private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID CAROL_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private ConversationMemberRepository conversationMemberRepository;

	@Mock
	private MessageRepository messageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private RealtimeEventPublisher realtimeEventPublisher;

	@Mock
	private AuditLogService auditLogService;

	@Mock
	private AuditJsonWriter auditJsonWriter;

	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(
				notificationRepository,
				conversationMemberRepository,
				messageRepository,
				userRepository,
				realtimeEventPublisher,
				auditLogService,
				auditJsonWriter);
		lenient().when(notificationRepository.defaultPreference(any(UUID.class), any(Instant.class)))
				.thenAnswer(invocation -> defaultPreference(invocation.getArgument(0), invocation.getArgument(1)));
	}

	@Test
	void messageCreatedCreatesMentionForMentionedMemberAndMessageForOtherMembers() {
		when(conversationMemberRepository.findMemberIds(CONVERSATION_ID)).thenReturn(List.of(ACTOR_ID, BOB_ID, CAROL_ID));
		when(userRepository.findActiveByUsernames(List.of("bob"))).thenReturn(List.of(activeUser(BOB_ID, "bob")));
		when(notificationRepository.findPreferencesByUserIds(List.of(BOB_ID, CAROL_ID))).thenReturn(Map.of());
		when(notificationRepository.countUnreadByUserIds(anyList())).thenReturn(Map.of(BOB_ID, 1, CAROL_ID, 1));

		notificationService.handleNotificationEvent(NotificationEvent.messageCreated(
				ACTOR_ID,
				CONVERSATION_ID,
				MESSAGE_ID,
				"@bob check giúp mình"));

		ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
		verify(notificationRepository).saveAll(captor.capture());

		List<Notification> notifications = captor.getValue();
		assertThat(notifications).hasSize(2);
		assertThat(notifications)
				.filteredOn(notification -> notification.userId().equals(BOB_ID))
				.singleElement()
				.extracting(Notification::type)
				.isEqualTo("MENTION");
		assertThat(notifications)
				.filteredOn(notification -> notification.userId().equals(CAROL_ID))
				.singleElement()
				.extracting(Notification::type)
				.isEqualTo("MESSAGE");
		assertThat(notifications).noneMatch(notification -> notification.userId().equals(ACTOR_ID));
	}

	@Test
	void messageCreatedHonorsDisabledMentionPreferenceWithoutFallingBackToMessage() {
		when(conversationMemberRepository.findMemberIds(CONVERSATION_ID)).thenReturn(List.of(ACTOR_ID, BOB_ID));
		when(userRepository.findActiveByUsernames(List.of("bob"))).thenReturn(List.of(activeUser(BOB_ID, "bob")));
		when(notificationRepository.findPreferencesByUserIds(List.of(BOB_ID))).thenReturn(Map.of(
				BOB_ID,
				new NotificationPreference(UUID.randomUUID(), BOB_ID, true, false, true, true, true, false, false, Instant.now(), Instant.now())));

		notificationService.handleNotificationEvent(NotificationEvent.messageCreated(
				ACTOR_ID,
				CONVERSATION_ID,
				MESSAGE_ID,
				"@bob check giúp mình"));

		verify(notificationRepository, never()).saveAll(anyList());
	}

	private NotificationPreference defaultPreference(UUID userId, Instant now) {
		return new NotificationPreference(UUID.randomUUID(), userId, true, true, true, true, true, false, false, now, now);
	}

	private User activeUser(UUID userId, String username) {
		Instant now = Instant.now();
		return new User(userId, username, username + "@example.com", "hash", username, null, "ACTIVE", "USER", true, "ACTIVE", true, 0, null, 0, null, null, now, now);
	}
}
