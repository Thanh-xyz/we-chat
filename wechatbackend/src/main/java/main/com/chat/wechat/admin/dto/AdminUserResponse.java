package main.com.chat.wechat.admin.dto;

import main.com.chat.wechat.user.model.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
		UUID id,
		String username,
		String email,
		String displayName,
		String avatarUrl,
		String presenceStatus,
		String accountStatus,
		boolean enabled,
		boolean emailVerified,
		int tokenVersion,
		List<String> roles,
		Instant lastLoginAt,
		int failedLoginCount,
		Instant lockedUntil,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt) {

	public static AdminUserResponse from(User user, List<String> roles) {
		return new AdminUserResponse(
				user.id(),
				user.username(),
				user.email(),
				user.displayName(),
				user.avatarUrl(),
				user.status(),
				user.accountStatus(),
				user.enabled(),
				user.emailVerified(),
				user.tokenVersion(),
				roles,
				user.lastLoginAt(),
				user.failedLoginCount(),
				user.lockedUntil(),
				user.deletedAt(),
				user.createdAt(),
				user.updatedAt());
	}
}
