package main.com.chat.wechat.friendship.model;

import java.time.Instant;
import java.util.UUID;

public record Friendship(
		UUID id,
		UUID userId,
		UUID friendId,
		FriendshipStatus status,
		Instant createdAt,
		Instant deletedAt) {
}
