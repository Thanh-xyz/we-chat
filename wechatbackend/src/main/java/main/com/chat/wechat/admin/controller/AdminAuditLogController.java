package main.com.chat.wechat.admin.controller;

import main.com.chat.wechat.audit.model.AuditLog;
import main.com.chat.wechat.audit.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/admin/audit-logs", "/admin/audit-logs"})
public class AdminAuditLogController {
	private final AuditLogRepository auditLogRepository;

	public AdminAuditLogController(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('AUDIT_READ')")
	public List<AuditLog> list(
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return auditLogRepository.findRecent(Math.min(Math.max(limit, 1), 100), Math.max(offset, 0));
	}
}
