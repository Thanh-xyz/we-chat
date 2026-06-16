package main.com.chat.wechat.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import main.com.chat.wechat.audit.dto.AuditLogDetailResponse;
import main.com.chat.wechat.audit.dto.AuditLogExportRequest;
import main.com.chat.wechat.audit.dto.AuditLogPageResponse;
import main.com.chat.wechat.audit.dto.AuditLogResponse;
import main.com.chat.wechat.audit.dto.AuditLogSearchRequest;
import main.com.chat.wechat.audit.model.AuditLog;
import main.com.chat.wechat.audit.model.AuditResult;
import main.com.chat.wechat.audit.repository.AuditLogRepository;
import main.com.chat.wechat.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogService.class);
	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_SEARCH_LIMIT = 200;
	private static final int MAX_EXPORT_LIMIT = 10_000;
	private static final Duration MAX_EXPORT_WINDOW = Duration.ofDays(31);
	private static final Map<String, String> ACTION_ALIASES = Map.ofEntries(
			Map.entry("AUTH_REGISTER", "USER_CREATE"),
			Map.entry("LOGIN_SUCCESS", "AUTH_LOGIN_SUCCESS"),
			Map.entry("LOGIN_FAILED", "AUTH_LOGIN_FAILED"),
			Map.entry("LOGOUT", "AUTH_LOGOUT"),
			Map.entry("LOGOUT_ALL", "AUTH_LOGOUT"),
			Map.entry("PASSWORD_CHANGED", "AUTH_PASSWORD_CHANGE"),
			Map.entry("PASSWORD_RESET", "AUTH_PASSWORD_CHANGE"),
			Map.entry("EMAIL_VERIFIED", "AUTH_EMAIL_VERIFY"),
			Map.entry("ADMIN_USER_BLOCK", "USER_STATUS_CHANGE"),
			Map.entry("ADMIN_USER_UNBLOCK", "USER_STATUS_CHANGE"),
			Map.entry("ADMIN_USER_SOFT_DELETE", "USER_DELETE"),
			Map.entry("ADMIN_USER_STATUS_UPDATE", "USER_STATUS_CHANGE"),
			Map.entry("ADMIN_USER_ROLE_ASSIGN", "USER_ROLE_CHANGE"),
			Map.entry("ADMIN_USER_ROLE_REMOVE", "USER_ROLE_CHANGE"),
			Map.entry("ADMIN_USER_ROLES_REPLACE", "USER_ROLE_CHANGE"),
			Map.entry("ADMIN_ROLE_CREATE", "SYSTEM_CONFIG_UPDATE"),
			Map.entry("ADMIN_ROLE_UPDATE", "SYSTEM_CONFIG_UPDATE"),
			Map.entry("ADMIN_ROLE_DELETE", "SYSTEM_CONFIG_UPDATE"),
			Map.entry("CONVERSATION_GROUP_UPDATE", "CONVERSATION_UPDATE"),
			Map.entry("CONVERSATION_MEMBER_LEAVE", "CONVERSATION_MEMBER_REMOVE"),
			Map.entry("ACCOUNT_LOCKED", "USER_STATUS_CHANGE"),
			Map.entry("ATTACHMENT_ACCESS_DENIED", "SECURITY_ACCESS_DENIED"));

	private final AuditLogRepository auditLogRepository;
	private final RequestContextProvider requestContextProvider;
	private final AuditSanitizer auditSanitizer;
	private final TransactionTemplate requiresNewTransaction;

	public AuditLogService(
			AuditLogRepository auditLogRepository,
			RequestContextProvider requestContextProvider,
			AuditSanitizer auditSanitizer,
			PlatformTransactionManager transactionManager) {
		this.auditLogRepository = auditLogRepository;
		this.requestContextProvider = requestContextProvider;
		this.auditSanitizer = auditSanitizer;
		this.requiresNewTransaction = new TransactionTemplate(transactionManager);
		this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public void log(String action, String resourceType, String resourceId, String beforeValue, String afterValue) {
		logSuccess(action, resourceType, resourceId, beforeValue, afterValue, null, null);
	}

	public void log(
			String action,
			String resourceType,
			String resourceId,
			String beforeValue,
			String afterValue,
			HttpServletRequest request) {
		logSuccess(action, resourceType, resourceId, beforeValue, afterValue, null, null);
	}

	public void logSuccess(String action, String resourceType, String resourceId, String beforeValue, String afterValue) {
		logSuccess(action, resourceType, resourceId, beforeValue, afterValue, null, null);
	}

	public void logSuccess(
			String action,
			String resourceType,
			String resourceId,
			String beforeValue,
			String afterValue,
			String metadata) {
		logSuccess(action, resourceType, resourceId, beforeValue, afterValue, metadata, null);
	}

	public void logSuccess(
			String action,
			String resourceType,
			String resourceId,
			String beforeValue,
			String afterValue,
			String metadata,
			HttpServletRequest request) {
		log(buildLog(action, resourceType, resourceId, beforeValue, afterValue, metadata, AuditResult.SUCCESS, null), false);
	}

	public void logFailure(
			String action,
			String resourceType,
			String resourceId,
			String failureReason,
			String metadata,
			HttpServletRequest request) {
		log(buildLog(action, resourceType, resourceId, null, null, metadata, AuditResult.FAILED, failureReason), false);
	}

	public void logFailure(String action, String resourceType, String resourceId, String failureReason, String metadata) {
		logFailure(action, resourceType, resourceId, failureReason, metadata, null);
	}

	public AuditLogPageResponse search(AuditLogSearchRequest request) {
		AuditLogSearchRequest safeRequest = safeSearchRequest(request);
		List<AuditLogResponse> items = auditLogRepository.search(safeRequest).stream()
				.map(AuditLogResponse::from)
				.toList();
		return new AuditLogPageResponse(items, auditLogRepository.count(safeRequest), safeRequest.limit(), safeRequest.offset());
	}

	public AuditLogDetailResponse findById(UUID id) {
		return auditLogRepository.findById(id)
				.map(AuditLogDetailResponse::from)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Audit log not found"));
	}

	public String export(AuditLogExportRequest request) {
		AuditLogExportRequest safeRequest = safeExportRequest(request);
		List<AuditLog> logs = auditLogRepository.export(safeRequest);
		StringBuilder csv = new StringBuilder("id,created_at,actor_user_id,actor_username,action,resource_type,resource_id,target_user_id,conversation_id,message_id,result,failure_reason,request_id,trace_id,ip_address,user_agent\n");
		for (AuditLog log : logs) {
			csv.append(csv(log.id()))
					.append(',').append(csv(log.createdAt()))
					.append(',').append(csv(log.actorUserId()))
					.append(',').append(csv(log.actorUsername()))
					.append(',').append(csv(log.action()))
					.append(',').append(csv(log.resourceType()))
					.append(',').append(csv(log.resourceId()))
					.append(',').append(csv(log.targetUserId()))
					.append(',').append(csv(log.conversationId()))
					.append(',').append(csv(log.messageId()))
					.append(',').append(csv(log.result()))
					.append(',').append(csv(log.failureReason()))
					.append(',').append(csv(log.requestId()))
					.append(',').append(csv(log.traceId()))
					.append(',').append(csv(log.ipAddress()))
					.append(',').append(csv(log.userAgent()))
					.append('\n');
		}
		return csv.toString();
	}

	private AuditLog buildLog(
			String action,
			String resourceType,
			String resourceId,
			String beforeValue,
			String afterValue,
			String metadata,
			AuditResult result,
			String failureReason) {
		RequestContextProvider.RequestContext context = requestContextProvider.current();
		String safeResourceType = StringUtils.hasText(resourceType) ? resourceType.trim().toUpperCase(Locale.ROOT) : "SYSTEM";
		String safeResourceId = StringUtils.hasText(resourceId) ? resourceId.trim() : null;
		String normalizedAction = normalizeAction(action);
		boolean newResourceInCurrentTransaction = normalizedAction.endsWith("_CREATE") || "ATTACHMENT_UPLOAD".equals(normalizedAction);
		return new AuditLog(
				UUID.randomUUID(),
				context.actorUserId(),
				context.actorUsername(),
				context.actorEmail(),
				normalizedAction,
				safeResourceType,
				safeResourceId,
				extractUuid(!newResourceInCurrentTransaction && "USER".equals(safeResourceType) ? safeResourceId : null),
				extractUuid(!newResourceInCurrentTransaction && "CONVERSATION".equals(safeResourceType) ? safeResourceId : null),
				extractUuid(!newResourceInCurrentTransaction && "MESSAGE".equals(safeResourceType) ? safeResourceId : null),
				context.requestId(),
				context.traceId(),
				auditSanitizer.sanitize(beforeValue),
				auditSanitizer.sanitize(afterValue),
				auditSanitizer.sanitize(metadata),
				context.ipAddress(),
				context.userAgent(),
				result,
				truncate(failureReason, 1_000),
				Instant.now());
	}

	private void log(AuditLog auditLog, boolean critical) {
		try {
			requiresNewTransaction.executeWithoutResult(status -> auditLogRepository.save(auditLog));
		} catch (RuntimeException exception) {
			if (critical) {
				throw exception;
			}
			LOGGER.warn("Audit log write failed action={} resourceType={} resourceId={}",
					auditLog.action(),
					auditLog.resourceType(),
					auditLog.resourceId(),
					exception);
		}
	}

	private AuditLogSearchRequest safeSearchRequest(AuditLogSearchRequest request) {
		int limit = request == null ? DEFAULT_LIMIT : request.limit();
		int offset = request == null ? 0 : request.offset();
		return new AuditLogSearchRequest(
				request == null ? null : normalizeNullableAction(request.action()),
				request == null ? null : request.actorUserId(),
				request == null ? null : request.targetUserId(),
				request == null ? null : request.conversationId(),
				request == null ? null : request.messageId(),
				request == null ? null : normalizeNullable(request.resourceType()),
				request == null ? null : normalizeNullable(request.resourceId()),
				request == null ? null : request.result(),
				request == null ? null : request.from(),
				request == null ? null : request.to(),
				Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT),
				Math.max(offset, 0));
	}

	private AuditLogExportRequest safeExportRequest(AuditLogExportRequest request) {
		if (request == null || request.from() == null || request.to() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Export requires from and to timestamps");
		}
		if (request.to().isBefore(request.from())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Export end time must be after start time");
		}
		if (Duration.between(request.from(), request.to()).compareTo(MAX_EXPORT_WINDOW) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Export range cannot exceed 31 days");
		}
		int limit = Math.min(Math.max(request.limit(), 1), MAX_EXPORT_LIMIT);
		return new AuditLogExportRequest(
				normalizeNullableAction(request.action()),
				request.actorUserId(),
				request.targetUserId(),
				request.conversationId(),
				request.messageId(),
				normalizeNullable(request.resourceType()),
				normalizeNullable(request.resourceId()),
				request.result(),
				request.from(),
				request.to(),
				limit);
	}

	private String normalizeAction(String action) {
		String normalized = normalizeNullable(action);
		if (normalized == null) {
			return "SYSTEM_CONFIG_UPDATE";
		}
		return ACTION_ALIASES.getOrDefault(normalized, normalized);
	}

	private String normalizeNullableAction(String action) {
		String normalized = normalizeNullable(action);
		if (normalized == null) {
			return null;
		}
		return ACTION_ALIASES.getOrDefault(normalized, normalized);
	}

	private String normalizeNullable(String value) {
		return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
	}

	private UUID extractUuid(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private String csv(Object value) {
		if (value == null) {
			return "";
		}
		String text = String.valueOf(value);
		return "\"" + text.replace("\"", "\"\"") + "\"";
	}
}
