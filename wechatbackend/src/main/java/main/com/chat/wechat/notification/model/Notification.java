package main.com.chat.wechat.notification.model;

import java.time.Instant;
import java.util.UUID;

public record Notification(
		UUID id,
		UUID userId,
		UUID actorUserId,
		UUID conversationId,
		UUID messageId,
		String type,
		String title,
		String content,
		boolean read,
		Instant createdAt,
		Instant readAt,
		Instant deletedAt) {
}
