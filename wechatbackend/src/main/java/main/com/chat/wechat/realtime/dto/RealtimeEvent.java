package main.com.chat.wechat.realtime.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RealtimeEvent(
		String type,
		UUID conversationId,
		UUID messageId,
		UUID actorUserId,
		UUID targetUserId,
		Map<String, Object> payload,
		Instant occurredAt) {

	public static RealtimeEvent of(
			String type,
			UUID conversationId,
			UUID messageId,
			UUID actorUserId,
			UUID targetUserId,
			Map<String, Object> payload) {
		return new RealtimeEvent(type, conversationId, messageId, actorUserId, targetUserId, payload, Instant.now());
	}
}
