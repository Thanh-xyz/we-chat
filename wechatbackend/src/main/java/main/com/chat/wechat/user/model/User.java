package main.com.chat.wechat.user.model;

import java.time.Instant;
import java.util.UUID;

public record User(
		UUID id,
		String username,
		String email,
		String passwordHash,
		String displayName,
		String avatarUrl,
		String status,
		String role,
		boolean enabled,
		String accountStatus,
		boolean emailVerified,
		int tokenVersion,
		Instant lastLoginAt,
		int failedLoginCount,
		Instant lockedUntil,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt) {

	public boolean active() {
		return "ACTIVE".equals(accountStatus) && deletedAt == null;
	}
}
