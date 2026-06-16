package main.com.chat.wechat.audit.dto;

import main.com.chat.wechat.audit.model.AuditLog;
import main.com.chat.wechat.audit.model.AuditResult;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
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
		AuditResult result,
		String failureReason,
		Instant createdAt) {
	public static AuditLogResponse from(AuditLog auditLog) {
		return new AuditLogResponse(
				auditLog.id(),
				auditLog.actorUserId(),
				auditLog.actorUsername(),
				auditLog.actorEmail(),
				auditLog.action(),
				auditLog.resourceType(),
				auditLog.resourceId(),
				auditLog.targetUserId(),
				auditLog.conversationId(),
				auditLog.messageId(),
				auditLog.requestId(),
				auditLog.traceId(),
				auditLog.result(),
				auditLog.failureReason(),
				auditLog.createdAt());
	}
}
