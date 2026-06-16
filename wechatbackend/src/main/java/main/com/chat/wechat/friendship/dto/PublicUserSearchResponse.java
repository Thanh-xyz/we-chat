package main.com.chat.wechat.friendship.dto;

import main.com.chat.wechat.friendship.model.RelationStatus;
import main.com.chat.wechat.user.model.User;

import java.util.UUID;

public record PublicUserSearchResponse(
		UUID userId,
		String username,
		String displayName,
		String avatarUrl,
		String status,
		RelationStatus relationStatus) {
	public static PublicUserSearchResponse from(User user, RelationStatus relationStatus) {
		return new PublicUserSearchResponse(
				user.id(),
				user.username(),
				user.displayName(),
				user.avatarUrl(),
				user.status(),
				relationStatus);
	}
}
