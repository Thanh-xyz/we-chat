package main.com.chat.wechat.message.service;

import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.conversation.service.ConversationService;
import main.com.chat.wechat.friendship.service.FriendshipService;
import main.com.chat.wechat.message.dto.AttachmentMetadataRequest;
import main.com.chat.wechat.message.dto.CreateMessageRequest;
import main.com.chat.wechat.message.dto.EditMessageRequest;
import main.com.chat.wechat.message.dto.MessageResponse;
import main.com.chat.wechat.message.dto.ReactionRequest;
import main.com.chat.wechat.message.model.Message;
import main.com.chat.wechat.message.model.MessageAttachment;
import main.com.chat.wechat.message.repository.MessageAttachmentRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
	private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

	@Mock
	private ConversationService conversationService;

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private MessageRepository messageRepository;

	@Mock
	private MessageAttachmentRepository messageAttachmentRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FriendshipService friendshipService;

	@Mock
	private AuditLogService auditLogService;

	@Mock
	private AuditJsonWriter auditJsonWriter;

	@Mock
	private RealtimeEventPublisher realtimeEventPublisher;

	@Mock
	private NotificationEventPublisher notificationEventPublisher;

	private MessageService messageService;

	@BeforeEach
	void setUp() {
		messageService = new MessageService(
				conversationService,
				conversationRepository,
				messageRepository,
				messageAttachmentRepository,
				userRepository,
				friendshipService,
				auditLogService,
				auditJsonWriter,
				realtimeEventPublisher,
				notificationEventPublisher);
		lenient().when(auditJsonWriter.write(any())).thenReturn("{}");
	}

	@Test
	void editMessageRejectsNonSender() {
		Message message = message(OTHER_USER_ID, "TEXT", Instant.now(), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());

		assertThatThrownBy(() -> messageService.edit(ACTOR_ID, MESSAGE_ID, new EditMessageRequest("new content")))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));

		verify(messageRepository, never()).updateContent(any(UUID.class), any(), any(Instant.class));
	}

	@Test
	void editMessageUpdatesOwnTextWithinWindow() {
		Message message = message(ACTOR_ID, "TEXT", Instant.now(), false, false);
		Message updated = new Message(
				message.id(),
				message.conversationId(),
				message.senderId(),
				"new content",
				message.messageType(),
				message.replyToMessageId(),
				Instant.now(),
				null,
				null,
				true,
				false,
				message.createdAt(),
				Instant.now());
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(messageRepository.updateContent(eq(MESSAGE_ID), eq("new content"), any(Instant.class))).thenReturn(updated);
		when(messageRepository.findReactionSummaries(MESSAGE_ID, ACTOR_ID)).thenReturn(List.of());

		MessageResponse response = messageService.edit(ACTOR_ID, MESSAGE_ID, new EditMessageRequest("new content"));

		assertThat(response.content()).isEqualTo("new content");
		assertThat(response.edited()).isTrue();
	}

	@Test
	void editMessageRejectsSystemMessage() {
		Message message = message(ACTOR_ID, "SYSTEM", Instant.now(), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());

		assertThatThrownBy(() -> messageService.edit(ACTOR_ID, MESSAGE_ID, new EditMessageRequest("new content")))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getMessage()).isEqualTo("System messages cannot be edited");
				});

		verify(messageRepository, never()).updateContent(any(UUID.class), any(), any(Instant.class));
	}

	@Test
	void editMessageRejectsExpiredWindow() {
		Message message = message(ACTOR_ID, "TEXT", Instant.now().minusSeconds(16 * 60), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());

		assertThatThrownBy(() -> messageService.edit(ACTOR_ID, MESSAGE_ID, new EditMessageRequest("new content")))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getMessage()).isEqualTo("Message edit window has expired");
				});

		verify(messageRepository, never()).updateContent(any(UUID.class), any(), any(Instant.class));
	}

	@Test
	void recallMessageReturnsRecalledDisplayText() {
		Message message = message(ACTOR_ID, "TEXT", Instant.now(), false, false);
		Message recalled = new Message(
				message.id(),
				message.conversationId(),
				message.senderId(),
				null,
				message.messageType(),
				message.replyToMessageId(),
				null,
				null,
				Instant.now(),
				false,
				true,
				message.createdAt(),
				Instant.now());
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(messageRepository.recall(eq(MESSAGE_ID), any(Instant.class))).thenReturn(recalled);
		when(messageRepository.findReactionSummaries(MESSAGE_ID, ACTOR_ID)).thenReturn(List.of());

		MessageResponse response = messageService.recall(ACTOR_ID, MESSAGE_ID);

		assertThat(response.recalled()).isTrue();
		assertThat(response.content()).isEqualTo("Tin nhắn đã được thu hồi");
	}

	@Test
	void recallMessageRejectsNonSender() {
		Message message = message(OTHER_USER_ID, "TEXT", Instant.now(), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());

		assertThatThrownBy(() -> messageService.recall(ACTOR_ID, MESSAGE_ID))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));

		verify(messageRepository, never()).recall(any(UUID.class), any(Instant.class));
	}

	@Test
	void recallMessageRejectsSystemMessage() {
		Message message = message(ACTOR_ID, "SYSTEM", Instant.now(), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());

		assertThatThrownBy(() -> messageService.recall(ACTOR_ID, MESSAGE_ID))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getMessage()).isEqualTo("System messages cannot be recalled");
				});

		verify(messageRepository, never()).recall(any(UUID.class), any(Instant.class));
	}

	@Test
	void recallMessageRejectsExpiredWindow() {
		Message message = message(ACTOR_ID, "TEXT", Instant.now().minusSeconds(25 * 60 * 60), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());

		assertThatThrownBy(() -> messageService.recall(ACTOR_ID, MESSAGE_ID))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getMessage()).isEqualTo("Message recall window has expired");
				});

		verify(messageRepository, never()).recall(any(UUID.class), any(Instant.class));
	}

	@Test
	void deleteForMeStoresUserDeletionOnly() {
		Message message = message(ACTOR_ID, "TEXT", Instant.now(), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());

		messageService.deleteForMe(ACTOR_ID, MESSAGE_ID);

		verify(messageRepository).deleteForUser(eq(MESSAGE_ID), eq(ACTOR_ID), any(Instant.class));
	}

	@Test
	void addReactionRequiresAccessibleMessage() {
		Message message = message(ACTOR_ID, "TEXT", Instant.now(), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(messageRepository.findReactionSummaries(MESSAGE_ID, ACTOR_ID)).thenReturn(List.of());

		messageService.addReaction(ACTOR_ID, MESSAGE_ID, new ReactionRequest("👍"));

		verify(messageRepository).addReaction(eq(MESSAGE_ID), eq(ACTOR_ID), eq("👍"), any(Instant.class));
	}

	@Test
	void deleteReactionRemovesOnlyCurrentUserEmoji() {
		Message message = message(ACTOR_ID, "TEXT", Instant.now(), false, false);
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(messageRepository.findReactionSummaries(MESSAGE_ID, ACTOR_ID)).thenReturn(List.of());

		messageService.deleteReaction(ACTOR_ID, MESSAGE_ID, new ReactionRequest("👍"));

		verify(messageRepository).deleteReaction(MESSAGE_ID, ACTOR_ID, "👍");
	}

	@Test
	void sendImageMessageRequiresAttachmentMetadata() {
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());

		assertThatThrownBy(() -> messageService.send(
				ACTOR_ID,
				CONVERSATION_ID,
				new CreateMessageRequest(null, "IMAGE", null, List.of(), List.of())))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getMessage()).isEqualTo("Attachment metadata or attachmentIds are required for IMAGE messages");
				});

		verify(messageRepository, never()).save(any(Message.class));
	}

	@Test
	void sendFileMessagePersistsAttachmentMetadata() {
		Message savedMessage = message(ACTOR_ID, "FILE", Instant.now(), false, false);
		MessageAttachment savedAttachment = new MessageAttachment(
				UUID.randomUUID(),
				MESSAGE_ID,
				ACTOR_ID,
				CONVERSATION_ID,
				"report.pdf",
				"https://cdn.example.com/report.pdf",
				"https://cdn.example.com/report.pdf",
				"application/pdf",
				"FILE",
				1024L,
				null,
				"CLEAN",
				null,
				Instant.now(),
				Instant.now());
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
		when(messageAttachmentRepository.save(any(MessageAttachment.class))).thenReturn(savedAttachment);
		when(conversationService.memberIds(CONVERSATION_ID)).thenReturn(List.of(ACTOR_ID, OTHER_USER_ID));

		MessageResponse response = messageService.send(
				ACTOR_ID,
				CONVERSATION_ID,
				new CreateMessageRequest(
						null,
						"FILE",
						null,
						List.of(),
						List.of(new AttachmentMetadataRequest(
								"report.pdf",
								"https://cdn.example.com/report.pdf",
								"application/pdf",
								1024L))));

		assertThat(response.attachments()).hasSize(1);
		assertThat(response.attachments().getFirst().fileName()).isEqualTo("report.pdf");
		verify(messageAttachmentRepository).save(any(MessageAttachment.class));
		verify(conversationRepository).updateLastMessage(eq(CONVERSATION_ID), eq(MESSAGE_ID), any(Instant.class));
	}

	@Test
	void sendMessageAttachesPendingAttachmentIds() {
		UUID attachmentId = UUID.fromString("00000000-0000-0000-0000-000000000030");
		Message savedMessage = message(ACTOR_ID, "IMAGE", Instant.now(), false, false);
		MessageAttachment pendingAttachment = attachment(attachmentId, null, "IMAGE");
		MessageAttachment attachedAttachment = attachment(attachmentId, MESSAGE_ID, "IMAGE");
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(activeUser(ACTOR_ID)));
		when(conversationService.findAccessibleConversation(ACTOR_ID, CONVERSATION_ID)).thenReturn(conversation());
		when(messageAttachmentRepository.findPendingByUploaderAndConversation(ACTOR_ID, CONVERSATION_ID, List.of(attachmentId)))
				.thenReturn(List.of(pendingAttachment));
		when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
		when(messageAttachmentRepository.attachToMessage(eq(attachmentId), eq(MESSAGE_ID), any(Instant.class)))
				.thenReturn(attachedAttachment);
		when(conversationService.memberIds(CONVERSATION_ID)).thenReturn(List.of(ACTOR_ID, OTHER_USER_ID));

		MessageResponse response = messageService.send(
				ACTOR_ID,
				CONVERSATION_ID,
				new CreateMessageRequest(null, "IMAGE", null, List.of(attachmentId), List.of()));

		assertThat(response.attachments()).hasSize(1);
		assertThat(response.attachments().getFirst().id()).isEqualTo(attachmentId);
		verify(messageAttachmentRepository).attachToMessage(eq(attachmentId), eq(MESSAGE_ID), any(Instant.class));
		verify(messageAttachmentRepository, never()).save(any(MessageAttachment.class));
	}

	private Message message(UUID senderId, String messageType, Instant createdAt, boolean edited, boolean recalled) {
		return new Message(
				MESSAGE_ID,
				CONVERSATION_ID,
				senderId,
				"content",
				messageType,
				null,
				edited ? createdAt : null,
				null,
				recalled ? createdAt : null,
				edited,
				recalled,
				createdAt,
				createdAt);
	}

	private Conversation conversation() {
		Instant now = Instant.now();
		return new Conversation(CONVERSATION_ID, "GROUP", "Group", null, ACTOR_ID, null, null, null, now, now);
	}

	private MessageAttachment attachment(UUID attachmentId, UUID messageId, String fileType) {
		Instant now = Instant.now();
		return new MessageAttachment(
				attachmentId,
				messageId,
				ACTOR_ID,
				CONVERSATION_ID,
				"image.png",
				"attachments/key.png",
				"/api/attachments/" + attachmentId + "/download",
				"image/png",
				fileType,
				128L,
				"checksum",
				"CLEAN",
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
