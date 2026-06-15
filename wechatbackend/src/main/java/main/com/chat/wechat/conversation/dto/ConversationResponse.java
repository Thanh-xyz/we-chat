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
		int unreadCount,
		UUID lastReadMessageId,
		Instant lastReadAt,
		boolean muted,
		Instant mutedUntil,
		Instant pinnedAt,
		Instant archivedAt,
		Instant createdAt,
		Instant updatedAt) {

	public static ConversationResponse from(Conversation conversation, List<UUID> memberIds) {
		return from(conversation, memberIds, 0);
	}

	public static ConversationResponse from(Conversation conversation, List<UUID> memberIds, int unreadCount) {
		return from(conversation, memberIds, unreadCount, null);
	}

	public static ConversationResponse from(
			Conversation conversation,
			List<UUID> memberIds,
			int unreadCount,
			main.com.chat.wechat.conversation.model.ConversationMember currentMember) {
		return new ConversationResponse(
				conversation.id(),
				conversation.type(),
				conversation.name(),
				conversation.avatarUrl(),
				conversation.createdBy(),
				conversation.lastMessageId(),
				conversation.lastMessageAt(),
				memberIds,
				unreadCount,
				currentMember == null ? null : currentMember.lastReadMessageId(),
				currentMember == null ? null : currentMember.readAt(),
				currentMember != null && currentMember.muted(),
				currentMember == null ? null : currentMember.mutedUntil(),
				currentMember == null ? null : currentMember.pinnedAt(),
				currentMember == null ? null : currentMember.archivedAt(),
				conversation.createdAt(),
				conversation.updatedAt());
	}
}
