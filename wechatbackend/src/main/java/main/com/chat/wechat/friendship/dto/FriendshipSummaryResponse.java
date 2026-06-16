package main.com.chat.wechat.friendship.dto;

public record FriendshipSummaryResponse(
		long friendCount,
		long incomingRequestCount,
		long outgoingRequestCount,
		long blockedCount) {
}
