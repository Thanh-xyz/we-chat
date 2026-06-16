package main.com.chat.wechat.friendship.model;

import java.time.Instant;
import java.util.UUID;

public record UserBlock(
		UUID id,
		UUID blockerId,
		UUID blockedId,
		String reason,
		Instant createdAt) {
}
