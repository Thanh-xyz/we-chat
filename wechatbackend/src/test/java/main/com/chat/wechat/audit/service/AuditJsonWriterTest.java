package main.com.chat.wechat.audit.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AuditJsonWriterTest {
	@Test
	void writeEscapesQuotesInsideValues() {
		AuditJsonWriter writer = new AuditJsonWriter(new ObjectMapper());

		String json = writer.write(new AuditValue("value with \"quotes\""));

		assertThat(json).isEqualTo("{\"value\":\"value with \\\"quotes\\\"\"}");
	}

	@Test
	void writeMasksSensitiveValues() {
		AuditJsonWriter writer = new AuditJsonWriter(new ObjectMapper());

		String json = writer.write(new SensitiveAuditValue("Password@123", "refresh-token", "Bearer raw-token"));

		assertThat(json).contains("\"password\":\"***MASKED***\"");
		assertThat(json).contains("\"refreshToken\":\"***MASKED***\"");
		assertThat(json).contains("\"authorization\":\"***MASKED***\"");
		assertThat(json).doesNotContain("Password@123", "refresh-token", "Bearer raw-token");
	}

	private record AuditValue(String value) {
	}

	private record SensitiveAuditValue(String password, String refreshToken, String authorization) {
	}
}
