package main.com.chat.wechat.conversation.dto;

import main.com.chat.wechat.user.model.User;

import java.util.UUID;

public record ConversationPeerResponse(
		UUID userId,
		String username,
		String displayName,
		String avatarUrl,
		String presenceStatus) {

	public static ConversationPeerResponse from(User user) {
		return new ConversationPeerResponse(
				user.id(),
				user.username(),
				user.displayName(),
				user.avatarUrl(),
				user.status());
	}
}
