package main.com.chat.wechat.conversation.dto;

import main.com.chat.wechat.conversation.model.Conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
		UUID id,
		String type,
		String name,
		String avatarUrl,
		UUID createdBy,
		UUID lastMessageId,
		Instant lastMessageAt,
		List<UUID> memberIds,
		Instant createdAt,
		Instant updatedAt) {

	public static ConversationResponse from(Conversation conversation, List<UUID> memberIds) {
		return new ConversationResponse(
				conversation.id(),
				conversation.type(),
				conversation.name(),
				conversation.avatarUrl(),
				conversation.createdBy(),
				conversation.lastMessageId(),
				conversation.lastMessageAt(),
				memberIds,
				conversation.createdAt(),
				conversation.updatedAt());
	}
}
