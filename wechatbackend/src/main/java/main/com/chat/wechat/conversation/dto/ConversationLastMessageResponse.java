package main.com.chat.wechat.conversation.dto;

import main.com.chat.wechat.message.model.Message;

import java.time.Instant;
import java.util.UUID;

public record ConversationLastMessageResponse(
		UUID id,
		UUID senderId,
		String messageType,
		String contentPreview,
		boolean recalled,
		Instant createdAt,
		boolean sentByCurrentUser,
		int attachmentCount) {
	private static final int PREVIEW_LIMIT = 160;

	public static ConversationLastMessageResponse from(Message message, UUID actorUserId, int attachmentCount) {
		boolean recalled = message.recalled() || message.recalledAt() != null;
		return new ConversationLastMessageResponse(
				message.id(),
				message.senderId(),
				message.messageType(),
				recalled ? "Tin nhắn đã được thu hồi" : preview(message.content()),
				recalled,
				message.createdAt(),
				message.senderId().equals(actorUserId),
				attachmentCount);
	}

	private static String preview(String content) {
		if (content == null || content.length() <= PREVIEW_LIMIT) {
			return content;
		}
		return content.substring(0, PREVIEW_LIMIT - 1) + "…";
	}
}
