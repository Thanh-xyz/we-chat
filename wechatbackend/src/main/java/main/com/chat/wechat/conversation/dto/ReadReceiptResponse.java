package main.com.chat.wechat.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ReadReceiptResponse(
		UUID conversationId,
		UUID userId,
		UUID lastReadMessageId,
		Instant lastReadAt,
		Instant readAt) {
}
