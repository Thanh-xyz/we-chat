package main.com.chat.wechat.notification.model;

import java.time.Instant;
import java.util.UUID;

public record NotificationPreference(
		UUID id,
		UUID userId,
		boolean messageEnabled,
		boolean mentionEnabled,
		boolean reactionEnabled,
		boolean groupEnabled,
		boolean systemEnabled,
		boolean emailEnabled,
		boolean pushEnabled,
		Instant createdAt,
		Instant updatedAt) {
}
