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

	private record AuditValue(String value) {
	}
}
