package main.com.chat.wechat.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import main.com.chat.wechat.audit.model.AuditLog;
import main.com.chat.wechat.audit.repository.AuditLogRepository;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditLogService {
	private final AuditLogRepository auditLogRepository;

	public AuditLogService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	public void log(String action, String targetType, String targetId, String beforeValue, String afterValue) {
		log(action, targetType, targetId, beforeValue, afterValue, null);
	}

	public void log(
			String action,
			String targetType,
			String targetId,
			String beforeValue,
			String afterValue,
			HttpServletRequest request) {
		auditLogRepository.save(new AuditLog(
				UUID.randomUUID(),
				currentActorId(),
				action,
				targetType,
				targetId,
				beforeValue,
				afterValue,
				request == null ? null : request.getRemoteAddr(),
				request == null ? null : request.getHeader("User-Agent"),
				Instant.now()));
	}

	private UUID currentActorId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			return null;
		}
		return user.id();
	}
}
