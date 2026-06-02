package main.com.chat.wechat.audit.model;

import java.time.Instant;
import java.util.UUID;

public record AuditLog(
		UUID id,
		UUID actorUserId,
		String action,
		String targetType,
		String targetId,
		String beforeValue,
		String afterValue,
		String ipAddress,
		String userAgent,
		Instant createdAt) {
}
