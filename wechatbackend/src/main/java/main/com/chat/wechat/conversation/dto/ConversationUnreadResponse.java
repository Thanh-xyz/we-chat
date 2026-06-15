package main.com.chat.wechat.conversation.dto;

import java.util.UUID;

public record ConversationUnreadResponse(
		UUID conversationId,
		Integer unreadCount,
		Integer totalUnreadCount) {

	public static ConversationUnreadResponse forConversation(UUID conversationId, int unreadCount) {
		return new ConversationUnreadResponse(conversationId, unreadCount, null);
	}

	public static ConversationUnreadResponse total(int totalUnreadCount) {
		return new ConversationUnreadResponse(null, null, totalUnreadCount);
	}
}
