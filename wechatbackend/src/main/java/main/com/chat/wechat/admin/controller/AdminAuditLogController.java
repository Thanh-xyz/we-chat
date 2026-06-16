package main.com.chat.wechat.admin.controller;

import main.com.chat.wechat.audit.dto.AuditLogDetailResponse;
import main.com.chat.wechat.audit.dto.AuditLogExportRequest;
import main.com.chat.wechat.audit.dto.AuditLogPageResponse;
import main.com.chat.wechat.audit.dto.AuditLogSearchRequest;
import main.com.chat.wechat.audit.model.AuditResult;
import main.com.chat.wechat.audit.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/audit-logs"})
public class AdminAuditLogController {
	private final AuditLogService auditLogService;

	public AdminAuditLogController(AuditLogService auditLogService) {
		this.auditLogService = auditLogService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('AUDIT_READ')")
	public AuditLogPageResponse list(
			@RequestParam(required = false) String action,
			@RequestParam(required = false) UUID actorUserId,
			@RequestParam(required = false) UUID targetUserId,
			@RequestParam(required = false) UUID conversationId,
			@RequestParam(required = false) UUID messageId,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String resourceId,
			@RequestParam(required = false) AuditResult result,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return auditLogService.search(new AuditLogSearchRequest(
				action,
				actorUserId,
				targetUserId,
				conversationId,
				messageId,
				resourceType,
				resourceId,
				result,
				from,
				to,
				limit,
				offset));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('AUDIT_READ')")
	public AuditLogDetailResponse detail(@PathVariable UUID id) {
		return auditLogService.findById(id);
	}

	@GetMapping(value = "/export", produces = "text/csv")
	@PreAuthorize("hasAuthority('AUDIT_EXPORT')")
	public String export(
			@RequestParam(required = false) String action,
			@RequestParam(required = false) UUID actorUserId,
			@RequestParam(required = false) UUID targetUserId,
			@RequestParam(required = false) UUID conversationId,
			@RequestParam(required = false) UUID messageId,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String resourceId,
			@RequestParam(required = false) AuditResult result,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "10000") int limit) {
		return auditLogService.export(new AuditLogExportRequest(
				action,
				actorUserId,
				targetUserId,
				conversationId,
				messageId,
				resourceType,
				resourceId,
				result,
				from,
				to,
				limit));
	}
}
