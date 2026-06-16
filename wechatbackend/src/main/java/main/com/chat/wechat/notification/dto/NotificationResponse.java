package main.com.chat.wechat.notification.dto;

import main.com.chat.wechat.notification.model.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
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
		Instant readAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
				notification.id(),
				notification.userId(),
				notification.actorUserId(),
				notification.conversationId(),
				notification.messageId(),
				notification.type(),
				notification.title(),
				notification.content(),
				notification.read(),
				notification.createdAt(),
				notification.readAt());
	}
}
