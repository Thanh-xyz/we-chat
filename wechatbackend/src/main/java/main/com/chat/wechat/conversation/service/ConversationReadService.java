package main.com.chat.wechat.conversation.service;

import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.dto.ConversationUnreadResponse;
import main.com.chat.wechat.conversation.dto.MarkConversationReadRequest;
import main.com.chat.wechat.conversation.dto.ReadConversationResponse;
import main.com.chat.wechat.conversation.dto.ReadReceiptResponse;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationReadService {
	private final ConversationService conversationService;
	private final ConversationMemberRepository conversationMemberRepository;
	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final RealtimeEventPublisher realtimeEventPublisher;

	public ConversationReadService(
			ConversationService conversationService,
			ConversationMemberRepository conversationMemberRepository,
			MessageRepository messageRepository,
			UserRepository userRepository,
			RealtimeEventPublisher realtimeEventPublisher) {
		this.conversationService = conversationService;
		this.conversationMemberRepository = conversationMemberRepository;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
		this.realtimeEventPublisher = realtimeEventPublisher;
	}

	@Transactional
	public ReadConversationResponse markRead(UUID actorUserId, UUID conversationId, MarkConversationReadRequest request) {
		findActiveUser(actorUserId);
		Conversation conversation = conversationService.findAccessibleConversation(actorUserId, conversationId);
		UUID lastReadMessageId = request == null ? null : request.lastReadMessageId();
		if (lastReadMessageId == null) {
			lastReadMessageId = messageRepository.findLatestMessageId(conversation.id(), actorUserId).orElse(null);
		} else if (!messageRepository.existsReadableInConversation(lastReadMessageId, conversation.id(), actorUserId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Read target message is not readable in this conversation");
		}

		Instant lastReadAt = lastReadMessageId == null
				? null
				: messageRepository.findCreatedAt(lastReadMessageId)
						.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Read target message is not readable in this conversation"));
		Instant readAt = Instant.now();
		conversationMemberRepository.updateLastRead(conversation.id(), actorUserId, lastReadMessageId, lastReadAt, readAt);
		int unreadCount = conversationMemberRepository.countUnread(conversation.id(), actorUserId);
		int totalUnreadCount = conversationMemberRepository.countTotalUnread(actorUserId, false);

		publishReadEvent(conversation.id(), actorUserId, lastReadMessageId, lastReadAt, readAt, unreadCount, totalUnreadCount);
		return new ReadConversationResponse(conversation.id(), actorUserId, lastReadMessageId, lastReadAt, readAt, unreadCount);
	}

	public ConversationUnreadResponse unreadCount(UUID actorUserId, UUID conversationId) {
		findActiveUser(actorUserId);
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		return ConversationUnreadResponse.forConversation(conversationId, conversationMemberRepository.countUnread(conversationId, actorUserId));
	}

	public ConversationUnreadResponse totalUnreadCount(UUID actorUserId, boolean includeArchived) {
		findActiveUser(actorUserId);
		return ConversationUnreadResponse.total(conversationMemberRepository.countTotalUnread(actorUserId, includeArchived));
	}

	public List<ReadReceiptResponse> readReceipts(UUID actorUserId, UUID conversationId) {
		findActiveUser(actorUserId);
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		return conversationMemberRepository.findReadReceiptsByConversationId(conversationId);
	}

	private void publishReadEvent(
			UUID conversationId,
			UUID actorUserId,
			UUID lastReadMessageId,
			Instant lastReadAt,
			Instant readAt,
			int unreadCount,
			int totalUnreadCount) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("conversationId", conversationId);
		payload.put("userId", actorUserId);
		payload.put("lastReadMessageId", lastReadMessageId);
		payload.put("lastReadAt", lastReadAt);
		payload.put("readAt", readAt);
		payload.put("unreadCount", unreadCount);
		payload.put("totalUnreadCount", totalUnreadCount);
		realtimeEventPublisher.publishToMembersAfterCommit(
				conversationService.memberIds(conversationId),
				RealtimeEvent.of("conversation.read", conversationId, lastReadMessageId, actorUserId, actorUserId, payload));
		realtimeEventPublisher.publishToUserAfterCommit(
				actorUserId,
				RealtimeEvent.of("conversation.unread.updated", conversationId, null, actorUserId, actorUserId, payload));
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User account is not active"));
	}
}
