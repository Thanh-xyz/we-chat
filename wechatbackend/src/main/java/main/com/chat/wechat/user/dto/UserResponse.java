package main.com.chat.wechat.user.dto;

import main.com.chat.wechat.user.model.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String username,
		String email,
		String displayName,
		String avatarUrl,
		String presenceStatus,
		String accountStatus,
		boolean emailVerified,
		List<String> roles,
		Instant lastLoginAt,
		Instant createdAt,
		Instant updatedAt) {

	public static UserResponse from(User user, List<String> roles) {
		return new UserResponse(
				user.id(),
				user.username(),
				user.email(),
				user.displayName(),
				user.avatarUrl(),
				user.status(),
				user.accountStatus(),
				user.emailVerified(),
				roles,
				user.lastLoginAt(),
				user.createdAt(),
				user.updatedAt());
	}
}
