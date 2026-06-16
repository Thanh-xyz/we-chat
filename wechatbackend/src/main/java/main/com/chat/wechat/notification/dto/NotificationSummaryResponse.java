package main.com.chat.wechat.notification.dto;

import java.util.List;

public record NotificationSummaryResponse(
		List<NotificationResponse> notifications,
		int unreadCount,
		int limit,
		int offset) {
}
