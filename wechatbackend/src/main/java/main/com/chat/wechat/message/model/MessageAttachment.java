package main.com.chat.wechat.message.model;

import java.time.Instant;
import java.util.UUID;

public record MessageAttachment(
		UUID id,
		UUID messageId,
		UUID uploaderId,
		UUID conversationId,
		String originalFileName,
		String storageKey,
		String fileUrl,
		String mimeType,
		String fileType,
		Long fileSize,
		String checksum,
		String scanStatus,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt,
		String storageStatus) {
	public MessageAttachment(
			UUID id,
			UUID messageId,
			UUID uploaderId,
			UUID conversationId,
			String originalFileName,
			String storageKey,
			String fileUrl,
			String mimeType,
			String fileType,
			Long fileSize,
			String checksum,
			String scanStatus,
			Instant deletedAt,
			Instant createdAt,
			Instant updatedAt) {
		this(id, messageId, uploaderId, conversationId, originalFileName, storageKey, fileUrl, mimeType,
				fileType, fileSize, checksum, scanStatus, deletedAt, createdAt, updatedAt, "READY");
	}
}
