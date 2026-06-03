package main.com.chat.wechat.role.model;

import java.time.Instant;
import java.util.UUID;

public record UserRole(
		UUID userId,
		UUID roleId,
		UUID assignedBy,
		Instant assignedAt) {
}
