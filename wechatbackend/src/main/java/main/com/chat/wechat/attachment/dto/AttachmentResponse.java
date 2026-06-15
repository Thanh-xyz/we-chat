package main.com.chat.wechat.attachment.dto;

import main.com.chat.wechat.message.model.MessageAttachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
		UUID id,
		UUID messageId,
		UUID uploaderId,
		UUID conversationId,
		String originalFileName,
		String fileUrl,
		String mimeType,
		String fileType,
		Long fileSize,
		String checksum,
		String scanStatus,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt) {
	public static AttachmentResponse from(MessageAttachment attachment) {
		return new AttachmentResponse(
				attachment.id(),
				attachment.messageId(),
				attachment.uploaderId(),
				attachment.conversationId(),
				attachment.originalFileName(),
				attachment.fileUrl(),
				attachment.mimeType(),
				attachment.fileType(),
				attachment.fileSize(),
				attachment.checksum(),
				attachment.scanStatus(),
				attachment.deletedAt(),
				attachment.createdAt(),
				attachment.updatedAt());
	}
}
