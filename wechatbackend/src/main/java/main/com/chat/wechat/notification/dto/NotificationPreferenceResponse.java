package main.com.chat.wechat.notification.dto;

import main.com.chat.wechat.notification.model.NotificationPreference;

import java.time.Instant;
import java.util.UUID;

public record NotificationPreferenceResponse(
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

	public static NotificationPreferenceResponse from(NotificationPreference preference) {
		return new NotificationPreferenceResponse(
				preference.userId(),
				preference.messageEnabled(),
				preference.mentionEnabled(),
				preference.reactionEnabled(),
				preference.groupEnabled(),
				preference.systemEnabled(),
				preference.emailEnabled(),
				preference.pushEnabled(),
				preference.createdAt(),
				preference.updatedAt());
	}
}
