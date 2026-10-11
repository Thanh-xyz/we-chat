package main.com.chat.wechat.attachment.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PresignedUploadResponse(
		UUID attachmentId,
		String uploadUrl,
		Map<String, String> headers,
		Instant expiresAt) {
}
