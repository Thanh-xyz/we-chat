package main.com.chat.wechat.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ReadConversationResponse(
		UUID conversationId,
		UUID userId,
		UUID lastReadMessageId,
		Instant readAt,
		int unreadCount) {
}
