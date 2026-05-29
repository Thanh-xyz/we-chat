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
		Instant createdAt,
		Instant updatedAt) {
}
