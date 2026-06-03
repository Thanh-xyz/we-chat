package main.com.chat.wechat.message.model;

import java.time.Instant;
import java.util.UUID;

public record Message(
		UUID id,
		UUID conversationId,
		UUID senderId,
		String content,
		String messageType,
		UUID replyToMessageId,
		Instant editedAt,
		Instant deletedAt,
		Instant recalledAt,
		Instant createdAt,
		Instant updatedAt) {
}
