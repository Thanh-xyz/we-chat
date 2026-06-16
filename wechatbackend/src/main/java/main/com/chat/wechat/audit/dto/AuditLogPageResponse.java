package main.com.chat.wechat.audit.dto;

import java.util.List;

public record AuditLogPageResponse(
		List<AuditLogResponse> items,
		long total,
		int limit,
		int offset) {
}
