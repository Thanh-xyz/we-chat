package main.com.chat.wechat.friendship.dto;

import main.com.chat.wechat.friendship.model.UserBlock;

import java.time.Instant;
import java.util.UUID;

public record BlockUserResponse(
		UUID id,
		UUID blockerId,
		UUID blockedId,
		String reason,
		Instant createdAt) {
	public static BlockUserResponse from(UserBlock block) {
		return new BlockUserResponse(
				block.id(),
				block.blockerId(),
				block.blockedId(),
				block.reason(),
				block.createdAt());
	}
}
