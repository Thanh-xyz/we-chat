package main.com.chat.wechat.realtime.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEnvelope(
		int version,
		UUID eventId,
		String originInstanceId,
		String destination,
		String eventType,
		JsonNode payload,
		Instant createdAt) {
	public static final int CURRENT_VERSION = 1;
}
