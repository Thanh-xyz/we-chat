package main.com.chat.wechat.message.service;

import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.conversation.service.ConversationService;
import main.com.chat.wechat.message.dto.CreateMessageRequest;
import main.com.chat.wechat.message.dto.MessageResponse;
import main.com.chat.wechat.message.model.Message;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {
	private final ConversationService conversationService;
	private final ConversationRepository conversationRepository;
	private final MessageRepository messageRepository;
	private final UserRepository userRepository;

	public MessageService(
			ConversationService conversationService,
			ConversationRepository conversationRepository,
			MessageRepository messageRepository,
			UserRepository userRepository) {
		this.conversationService = conversationService;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public MessageResponse send(UUID actorUserId, UUID conversationId, CreateMessageRequest request) {
		findActiveUser(actorUserId);
		Conversation conversation = conversationService.findAccessibleConversation(actorUserId, conversationId);
		String messageType = StringUtils.hasText(request.messageType()) ? request.messageType() : "TEXT";
		String content = normalizeContent(messageType, request.content());
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
				now,
				now));
		conversationRepository.updateLastMessage(conversation.id(), message.id(), now);
		return MessageResponse.from(message);
	}

	public List<MessageResponse> list(UUID actorUserId, UUID conversationId, int limit, int offset) {
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		return messageRepository.findByConversationId(conversationId, safeLimit, safeOffset).stream()
				.map(MessageResponse::from)
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
}
