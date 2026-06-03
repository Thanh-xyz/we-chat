package main.com.chat.wechat.message.dto;

import main.com.chat.wechat.message.model.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
		UUID id,
		UUID conversationId,
		UUID senderId,
		String content,
		String messageType,
		UUID replyToMessageId,
		Instant editedAt,
		Instant recalledAt,
		Instant createdAt,
		Instant updatedAt) {

	public static MessageResponse from(Message message) {
		return new MessageResponse(
				message.id(),
				message.conversationId(),
				message.senderId(),
				message.recalledAt() == null ? message.content() : null,
				message.messageType(),
				message.replyToMessageId(),
				message.editedAt(),
				message.recalledAt(),
				message.createdAt(),
				message.updatedAt());
	}
}
