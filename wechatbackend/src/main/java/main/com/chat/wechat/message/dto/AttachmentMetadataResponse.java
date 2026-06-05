package main.com.chat.wechat.message.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentMetadataResponse(
		UUID id,
		UUID messageId,
		String fileName,
		String fileUrl,
		String fileType,
		Long fileSize,
		Instant createdAt) {
}
