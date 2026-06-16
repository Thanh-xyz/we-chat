package main.com.chat.wechat.audit.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuditJsonWriter {
	private final ObjectMapper objectMapper;
	private final AuditSanitizer auditSanitizer;

	public AuditJsonWriter(ObjectMapper objectMapper) {
		this(objectMapper, new AuditSanitizer());
	}

	@Autowired
	public AuditJsonWriter(ObjectMapper objectMapper, AuditSanitizer auditSanitizer) {
		this.objectMapper = objectMapper;
		this.auditSanitizer = auditSanitizer;
	}

	public String write(Object value) {
		try {
			return auditSanitizer.sanitize(objectMapper.writeValueAsString(value));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not serialize audit value", exception);
		}
	}
}
