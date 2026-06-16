package main.com.chat.wechat.friendship.model;

import java.time.Instant;
import java.util.UUID;

public record FriendRequest(
		UUID id,
		UUID requesterId,
		UUID receiverId,
		FriendRequestStatus status,
		String message,
		Instant respondedAt,
		Instant expiresAt,
		Instant createdAt,
		Instant updatedAt) {
}
