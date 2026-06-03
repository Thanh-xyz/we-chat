package main.com.chat.wechat.conversation.model;

import java.time.Instant;
import java.util.UUID;

public record Conversation(
		UUID id,
		String type,
		String name,
		String avatarUrl,
		UUID createdBy,
		UUID lastMessageId,
		Instant lastMessageAt,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt) {
}
