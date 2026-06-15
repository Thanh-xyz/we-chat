package main.com.chat.wechat.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record TypingEventResponse(
		UUID conversationId,
		UUID userId,
		boolean typing,
		String eventType,
		Instant occurredAt,
		boolean published) {
}
