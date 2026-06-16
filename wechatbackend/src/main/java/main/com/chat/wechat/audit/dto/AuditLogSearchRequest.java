package main.com.chat.wechat.audit.dto;

import main.com.chat.wechat.audit.model.AuditResult;

import java.time.Instant;
import java.util.UUID;

public record AuditLogSearchRequest(
		String action,
		UUID actorUserId,
		UUID targetUserId,
		UUID conversationId,
		UUID messageId,
		String resourceType,
		String resourceId,
		AuditResult result,
		Instant from,
		Instant to,
		int limit,
		int offset) {
}
