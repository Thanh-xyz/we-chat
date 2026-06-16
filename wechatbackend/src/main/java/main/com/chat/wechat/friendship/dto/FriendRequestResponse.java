package main.com.chat.wechat.friendship.dto;

import main.com.chat.wechat.friendship.model.FriendRequest;
import main.com.chat.wechat.friendship.model.FriendRequestStatus;
import main.com.chat.wechat.user.model.User;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestResponse(
		UUID id,
		FriendUserSummary requester,
		FriendUserSummary receiver,
		FriendRequestStatus status,
		String message,
		Instant createdAt,
		Instant respondedAt,
		Instant expiresAt) {
	public static FriendRequestResponse from(FriendRequest request, User requester, User receiver) {
		return new FriendRequestResponse(
				request.id(),
				FriendUserSummary.from(requester),
				FriendUserSummary.from(receiver),
				request.status(),
				request.message(),
				request.createdAt(),
				request.respondedAt(),
				request.expiresAt());
	}
}
