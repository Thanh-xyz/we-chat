package main.com.chat.wechat.role.model;

import java.time.Instant;
import java.util.UUID;

public record Role(
		UUID id,
		String code,
		String name,
		String description,
		boolean systemRole,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt) {
}
