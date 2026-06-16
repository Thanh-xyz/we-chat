package main.com.chat.wechat.audit.dto;

import main.com.chat.wechat.audit.model.AuditLog;
import main.com.chat.wechat.audit.model.AuditResult;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDetailResponse(
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
		AuditResult result,
		String failureReason,
		String ipAddress,
		String userAgent,
		Instant createdAt) {
	public static AuditLogDetailResponse from(AuditLog auditLog) {
		return new AuditLogDetailResponse(
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
				auditLog.beforeValue(),
				auditLog.afterValue(),
				auditLog.metadata(),
				auditLog.result(),
				auditLog.failureReason(),
				auditLog.ipAddress(),
				auditLog.userAgent(),
				auditLog.createdAt());
	}
}
