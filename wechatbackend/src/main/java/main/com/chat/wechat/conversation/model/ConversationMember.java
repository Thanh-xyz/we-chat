package main.com.chat.wechat.conversation.model;

import java.time.Instant;
import java.util.UUID;

public record ConversationMember(
		UUID conversationId,
		UUID userId,
		String memberRole,
		String nickname,
		Instant joinedAt,
		Instant leftAt,
		Instant mutedUntil,
		Instant pinnedAt,
		UUID lastReadMessageId,
		boolean muted) {
}
