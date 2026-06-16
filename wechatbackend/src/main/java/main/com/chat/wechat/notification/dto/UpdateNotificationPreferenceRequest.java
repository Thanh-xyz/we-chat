package main.com.chat.wechat.notification.dto;

public record UpdateNotificationPreferenceRequest(
		Boolean messageEnabled,
		Boolean mentionEnabled,
		Boolean reactionEnabled,
		Boolean groupEnabled,
		Boolean systemEnabled,
		Boolean emailEnabled,
		Boolean pushEnabled) {
}
