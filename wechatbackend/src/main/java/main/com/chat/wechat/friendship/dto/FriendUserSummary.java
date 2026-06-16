package main.com.chat.wechat.friendship.dto;

import main.com.chat.wechat.user.model.User;

import java.util.UUID;

public record FriendUserSummary(
		UUID userId,
		String username,
		String email,
		String displayName,
		String avatarUrl,
		String status) {
	public static FriendUserSummary from(User user) {
		return new FriendUserSummary(
				user.id(),
				user.username(),
				user.email(),
				user.displayName(),
				user.avatarUrl(),
				user.status());
	}
}
