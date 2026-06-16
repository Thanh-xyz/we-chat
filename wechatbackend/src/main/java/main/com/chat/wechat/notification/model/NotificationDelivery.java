package main.com.chat.wechat.notification.model;

import java.time.Instant;
import java.util.UUID;

public record NotificationDelivery(
		UUID id,
		UUID notificationId,
		String channel,
		String status,
		Instant sentAt,
		Instant createdAt) {
}
