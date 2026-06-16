package main.com.chat.wechat.friendship.dto;

import main.com.chat.wechat.user.model.User;

import java.time.Instant;
import java.util.UUID;

public record FriendResponse(
		UUID userId,
		String username,
		String email,
		String displayName,
		String avatarUrl,
		String status,
		Instant friendshipCreatedAt) {
	public static FriendResponse from(User user, Instant friendshipCreatedAt) {
		return new FriendResponse(
				user.id(),
				user.username(),
				user.email(),
				user.displayName(),
				user.avatarUrl(),
				user.status(),
				friendshipCreatedAt);
	}
}
