package main.com.chat.wechat.message.model;

import java.time.Instant;
import java.util.UUID;

public record MessageAttachment(
		UUID id,
		UUID messageId,
		String fileName,
		String fileUrl,
		String fileType,
		Long fileSize,
		Instant createdAt) {
}
