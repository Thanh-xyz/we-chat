package main.com.chat.wechat.audit.service;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuditJsonWriter {
	private final ObjectMapper objectMapper;

	public AuditJsonWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not serialize audit value", exception);
		}
	}
}
