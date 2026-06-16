package main.com.chat.wechat.message.service;

import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.conversation.service.ConversationService;
import main.com.chat.wechat.message.dto.CreateMessageRequest;
import main.com.chat.wechat.message.dto.EditMessageRequest;
import main.com.chat.wechat.message.dto.MessageReactionResponse;
import main.com.chat.wechat.message.dto.MessageResponse;
import main.com.chat.wechat.message.dto.ReactionRequest;
import main.com.chat.wechat.message.model.Message;
import main.com.chat.wechat.message.model.MessageAttachment;
import main.com.chat.wechat.message.repository.MessageAttachmentRepository;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.notification.event.NotificationEvent;
import main.com.chat.wechat.notification.event.NotificationEventPublisher;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MessageService {
	private static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
	private static final Duration RECALL_WINDOW = Duration.ofHours(24);
	private static final Set<String> CLIENT_MESSAGE_TYPES = Set.of("TEXT", "IMAGE", "FILE", "VOICE");

	private final ConversationService conversationService;
	private final ConversationRepository conversationRepository;
	private final MessageRepository messageRepository;
	private final MessageAttachmentRepository messageAttachmentRepository;
	private final UserRepository userRepository;
	private final AuditLogService auditLogService;
	private final AuditJsonWriter auditJsonWriter;
	private final RealtimeEventPublisher realtimeEventPublisher;
	private final NotificationEventPublisher notificationEventPublisher;

	public MessageService(
			ConversationService conversationService,
			ConversationRepository conversationRepository,
			MessageRepository messageRepository,
			MessageAttachmentRepository messageAttachmentRepository,
			UserRepository userRepository,
			AuditLogService auditLogService,
			AuditJsonWriter auditJsonWriter,
			RealtimeEventPublisher realtimeEventPublisher,
			NotificationEventPublisher notificationEventPublisher) {
		this.conversationService = conversationService;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.messageAttachmentRepository = messageAttachmentRepository;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
		this.auditJsonWriter = auditJsonWriter;
		this.realtimeEventPublisher = realtimeEventPublisher;
		this.notificationEventPublisher = notificationEventPublisher;
	}

	@Transactional
	public MessageResponse send(UUID actorUserId, UUID conversationId, CreateMessageRequest request) {
		findActiveUser(actorUserId);
		Conversation conversation = conversationService.findAccessibleConversation(actorUserId, conversationId);
		String messageType = normalizeClientMessageType(request.messageType());
		String content = normalizeContent(messageType, request.content());
		List<MessageAttachment> pendingAttachments = resolvePendingAttachments(actorUserId, conversation.id(), messageType, request);
		validateAttachments(messageType, request, pendingAttachments);
		if (request.replyToMessageId() != null
				&& !messageRepository.existsInConversation(request.replyToMessageId(), conversation.id())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Reply target message is not in this conversation");
		}

		Instant now = Instant.now();
		Message message = messageRepository.save(new Message(
				UUID.randomUUID(),
				conversation.id(),
				actorUserId,
				content,
				messageType,
				request.replyToMessageId(),
				null,
				null,
				null,
				false,
				false,
				now,
				now));
		List<MessageAttachment> attachments = saveAttachments(message.id(), actorUserId, conversation.id(), request, pendingAttachments, now);
		conversationRepository.updateLastMessage(conversation.id(), message.id(), now);
		MessageResponse response = MessageResponse.from(message, List.of(), attachments);
		publishConversationEvent(
				conversation.id(),
				RealtimeEvent.of("message.created", conversation.id(), message.id(), actorUserId, null, Map.of(
						"messageId", message.id(),
						"attachments", response.attachments())));
		notificationEventPublisher.publish(NotificationEvent.messageCreated(actorUserId, conversation.id(), message.id(), content));
		publishUnreadUpdates(conversation.id(), actorUserId);
		return response;
	}

	public List<MessageResponse> list(UUID actorUserId, UUID conversationId, int limit, int offset) {
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		return toResponses(actorUserId, messageRepository.findByConversationId(conversationId, actorUserId, safeLimit, safeOffset));
	}

	public List<MessageResponse> search(UUID actorUserId, UUID conversationId, String query, int limit, int offset) {
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		return toResponses(actorUserId, messageRepository.search(conversationId, actorUserId, query, safeLimit, safeOffset));
	}

	@Transactional
	public MessageResponse edit(UUID actorUserId, UUID messageId, EditMessageRequest request) {
		findActiveUser(actorUserId);
		Message message = findAccessibleMessage(actorUserId, messageId);
		if (!message.senderId().equals(actorUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only the sender can edit this message");
		}
		if ("SYSTEM".equals(message.messageType())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "System messages cannot be edited");
		}
		if (message.recalled() || message.recalledAt() != null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Recalled messages cannot be edited");
		}
		Instant now = Instant.now();
		if (message.createdAt().plus(EDIT_WINDOW).isBefore(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Message edit window has expired");
		}
		String content = normalizeContent("TEXT", request.content());
		Message updated = messageRepository.updateContent(message.id(), content, now);
		auditLogService.log(
				"MESSAGE_EDIT",
				"MESSAGE",
				message.id().toString(),
				auditJsonWriter.write(new MessageContentAuditValue(message.content())),
				auditJsonWriter.write(new MessageContentAuditValue(content)));
		MessageResponse response = MessageResponse.from(
				updated,
				messageRepository.findReactionSummaries(updated.id(), actorUserId),
				messageAttachmentRepository.findByMessageId(updated.id()));
		publishConversationEvent(
				updated.conversationId(),
				RealtimeEvent.of("message.edited", updated.conversationId(), updated.id(), actorUserId, null, Map.of("messageId", updated.id())));
		return response;
	}

	@Transactional
	public MessageResponse recall(UUID actorUserId, UUID messageId) {
		findActiveUser(actorUserId);
		Message message = findAccessibleMessage(actorUserId, messageId);
		if (!message.senderId().equals(actorUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only the sender can recall this message");
		}
		if ("SYSTEM".equals(message.messageType())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "System messages cannot be recalled");
		}
		if (message.recalled() || message.recalledAt() != null) {
			return MessageResponse.from(message, messageRepository.findReactionSummaries(message.id(), actorUserId));
		}
		Instant now = Instant.now();
		if (message.createdAt().plus(RECALL_WINDOW).isBefore(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Message recall window has expired");
		}
		Message recalled = messageRepository.recall(message.id(), now);
		messageAttachmentRepository.softDeleteByMessageId(message.id(), now);
		auditLogService.log("MESSAGE_RECALL", "MESSAGE", message.id().toString(), null, auditJsonWriter.write(new MessageRecallAuditValue(true)));
		MessageResponse response = MessageResponse.from(recalled, messageRepository.findReactionSummaries(recalled.id(), actorUserId));
		publishConversationEvent(
				recalled.conversationId(),
				RealtimeEvent.of("message.recalled", recalled.conversationId(), recalled.id(), actorUserId, null, Map.of("messageId", recalled.id())));
		publishUnreadUpdates(recalled.conversationId(), actorUserId);
		return response;
	}

	@Transactional
	public void deleteForMe(UUID actorUserId, UUID messageId) {
		findActiveUser(actorUserId);
		Message message = findAccessibleMessage(actorUserId, messageId);
		messageRepository.deleteForUser(message.id(), actorUserId, Instant.now());
		auditLogService.log("MESSAGE_DELETE_FOR_ME", "MESSAGE", message.id().toString(), null, auditJsonWriter.write(new UserIdAuditValue(actorUserId)));
		realtimeEventPublisher.publishToUserAfterCommit(
				actorUserId,
				RealtimeEvent.of("message.deleted_for_me", message.conversationId(), message.id(), actorUserId, actorUserId, Map.of("messageId", message.id())));
		publishUnreadUpdate(message.conversationId(), actorUserId);
	}

	@Transactional
	public List<MessageReactionResponse> addReaction(UUID actorUserId, UUID messageId, ReactionRequest request) {
		findActiveUser(actorUserId);
		Message message = findAccessibleMessage(actorUserId, messageId);
		if (message.recalled() || message.recalledAt() != null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot react to recalled messages");
		}
		String emoji = normalizeEmoji(request.emoji());
		messageRepository.addReaction(message.id(), actorUserId, emoji, Instant.now());
		List<MessageReactionResponse> reactions = messageRepository.findReactionSummaries(message.id(), actorUserId);
		publishConversationEvent(
				message.conversationId(),
				RealtimeEvent.of("message.reaction.added", message.conversationId(), message.id(), actorUserId, null, Map.of("messageId", message.id(), "emoji", emoji)));
		notificationEventPublisher.publish(NotificationEvent.messageReaction(actorUserId, message.conversationId(), message.id(), message.senderId(), emoji));
		return reactions;
	}

	@Transactional
	public List<MessageReactionResponse> deleteReaction(UUID actorUserId, UUID messageId, ReactionRequest request) {
		findActiveUser(actorUserId);
		Message message = findAccessibleMessage(actorUserId, messageId);
		String emoji = normalizeEmoji(request.emoji());
		messageRepository.deleteReaction(message.id(), actorUserId, emoji);
		List<MessageReactionResponse> reactions = messageRepository.findReactionSummaries(message.id(), actorUserId);
		publishConversationEvent(
				message.conversationId(),
				RealtimeEvent.of("message.reaction.removed", message.conversationId(), message.id(), actorUserId, null, Map.of("messageId", message.id(), "emoji", emoji)));
		return reactions;
	}

	private Message findAccessibleMessage(UUID actorUserId, UUID messageId) {
		Message message = messageRepository.findById(messageId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
		conversationService.findAccessibleConversation(actorUserId, message.conversationId());
		return message;
	}

	private List<MessageResponse> toResponses(UUID actorUserId, List<Message> messages) {
		List<UUID> messageIds = messages.stream().map(Message::id).toList();
		Map<UUID, List<MessageReactionResponse>> reactionsByMessageId =
				messageRepository.findReactionSummariesByMessageIds(messageIds, actorUserId);
		Map<UUID, List<MessageAttachment>> attachmentsByMessageId =
				messageAttachmentRepository.findByMessageIds(messageIds);
		return messages.stream()
				.map(message -> MessageResponse.from(
						message,
						reactionsByMessageId.getOrDefault(message.id(), List.of()),
						attachmentsByMessageId.getOrDefault(message.id(), List.of())))
				.toList();
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User account is not active"));
	}

	private String normalizeContent(String messageType, String content) {
		if ("TEXT".equals(messageType) && !StringUtils.hasText(content)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Text messages require content");
		}
		return StringUtils.hasText(content) ? content.trim() : null;
	}

	private String normalizeClientMessageType(String messageType) {
		String normalized = StringUtils.hasText(messageType)
				? messageType.trim().toUpperCase(Locale.ROOT)
				: "TEXT";
		if ("SYSTEM".equals(normalized)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "System messages can only be created by backend workflow");
		}
		if (!CLIENT_MESSAGE_TYPES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported message type: " + normalized);
		}
		return normalized;
	}

	private void validateAttachments(String messageType, CreateMessageRequest request, List<MessageAttachment> pendingAttachments) {
		boolean hasAttachments = !pendingAttachments.isEmpty()
				|| (request.attachments() != null && !request.attachments().isEmpty());
		if (("IMAGE".equals(messageType) || "FILE".equals(messageType) || "VOICE".equals(messageType)) && !hasAttachments) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Attachment metadata or attachmentIds are required for " + messageType + " messages");
		}
	}

	private List<MessageAttachment> resolvePendingAttachments(
			UUID actorUserId,
			UUID conversationId,
			String messageType,
			CreateMessageRequest request) {
		if (request.attachmentIds() == null || request.attachmentIds().isEmpty()) {
			return List.of();
		}
		if (request.attachmentIds().stream().anyMatch(id -> id == null)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Attachment id cannot be null");
		}
		List<UUID> attachmentIds = new LinkedHashSet<>(request.attachmentIds()).stream().toList();
		List<MessageAttachment> attachments = messageAttachmentRepository.findPendingByUploaderAndConversation(
				actorUserId,
				conversationId,
				attachmentIds);
		if (attachments.size() != attachmentIds.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "One or more attachments are invalid, already attached, or not in this conversation");
		}
		if (("IMAGE".equals(messageType) || "FILE".equals(messageType) || "VOICE".equals(messageType))
				&& attachments.stream().anyMatch(attachment -> !messageType.equals(attachment.fileType()))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Attachment type does not match message type");
		}
		return attachments;
	}

	private List<MessageAttachment> saveAttachments(
			UUID messageId,
			UUID actorUserId,
			UUID conversationId,
			CreateMessageRequest request,
			List<MessageAttachment> pendingAttachments,
			Instant now) {
		if (!pendingAttachments.isEmpty()) {
			return pendingAttachments.stream()
					.map(attachment -> messageAttachmentRepository.attachToMessage(attachment.id(), messageId, now))
					.toList();
		}
		if (request.attachments() == null || request.attachments().isEmpty()) {
			return List.of();
		}
		return request.attachments().stream()
				.map(attachment -> messageAttachmentRepository.save(new MessageAttachment(
						UUID.randomUUID(),
						messageId,
						actorUserId,
						conversationId,
						attachment.fileName().trim(),
						attachment.fileUrl().trim(),
						attachment.fileUrl().trim(),
						normalizeLegacyMimeType(attachment.fileType()),
						normalizeLegacyFileType(attachment.fileType(), request.messageType()),
						attachment.fileSize(),
						null,
						"CLEAN",
						null,
						now,
						now)))
				.toList();
	}

	private String normalizeLegacyMimeType(String fileType) {
		return StringUtils.hasText(fileType) ? fileType.trim().toLowerCase(Locale.ROOT) : "application/octet-stream";
	}

	private String normalizeLegacyFileType(String fileType, String messageType) {
		String normalizedMessageType = normalizeClientMessageType(messageType);
		if ("IMAGE".equals(normalizedMessageType) || "FILE".equals(normalizedMessageType) || "VOICE".equals(normalizedMessageType)) {
			return normalizedMessageType;
		}
		if (!StringUtils.hasText(fileType)) {
			return "FILE";
		}
		String normalized = fileType.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("image/")) {
			return "IMAGE";
		}
		if (normalized.startsWith("audio/")) {
			return "VOICE";
		}
		return "FILE";
	}

	private void publishConversationEvent(UUID conversationId, RealtimeEvent event) {
		realtimeEventPublisher.publishToMembersAfterCommit(
				conversationService.memberIds(conversationId),
				event);
	}

	private void publishUnreadUpdates(UUID conversationId, UUID excludedUserId) {
		List<UUID> memberIds = conversationService.memberIds(conversationId);
		if (memberIds == null || memberIds.isEmpty()) {
			return;
		}
		memberIds.stream()
				.filter(userId -> !userId.equals(excludedUserId))
				.forEach(userId -> publishUnreadUpdate(conversationId, userId));
	}

	private void publishUnreadUpdate(UUID conversationId, UUID userId) {
		int unreadCount = messageRepository.countUnreadByConversationIds(userId, List.of(conversationId))
				.getOrDefault(conversationId, 0);
		int totalUnreadCount = messageRepository.countTotalUnread(userId, false);
		realtimeEventPublisher.publishToUserAfterCommit(
				userId,
				RealtimeEvent.of("conversation.unread.updated", conversationId, null, userId, userId, Map.of(
						"conversationId", conversationId,
						"unreadCount", unreadCount,
						"totalUnreadCount", totalUnreadCount)));
	}

	private String normalizeEmoji(String emoji) {
		if (!StringUtils.hasText(emoji)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Emoji is required");
		}
		return emoji.trim();
	}

	private record MessageContentAuditValue(String content) {
	}

	private record MessageRecallAuditValue(boolean isRecalled) {
	}

	private record UserIdAuditValue(UUID userId) {
	}
}
