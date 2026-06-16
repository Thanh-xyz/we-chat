package main.com.chat.wechat.audit.model;

import java.time.Instant;
import java.util.UUID;

public record AuditLog(
		UUID id,
		UUID actorUserId,
		String actorUsername,
		String actorEmail,
		String action,
		String resourceType,
		String resourceId,
		UUID targetUserId,
		UUID conversationId,
		UUID messageId,
		String requestId,
		String traceId,
		String beforeValue,
		String afterValue,
		String metadata,
		String ipAddress,
		String userAgent,
		AuditResult result,
		String failureReason,
		Instant createdAt) {
}
