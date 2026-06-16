package main.com.chat.wechat.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationRealtimeEvent(
		String eventType,
		UUID notificationId,
		UUID userId,
		Integer unreadCount,
		Map<String, Object> payload,
		Instant occurredAt) {

	public static NotificationRealtimeEvent of(
			String eventType,
			UUID notificationId,
			UUID userId,
			Integer unreadCount,
			Map<String, Object> payload) {
		return new NotificationRealtimeEvent(eventType, notificationId, userId, unreadCount, payload, Instant.now());
	}
}
