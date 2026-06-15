package main.com.chat.wechat.attachment.dto;

import main.com.chat.wechat.message.model.MessageAttachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentUploadResponse(
		UUID id,
		UUID conversationId,
		UUID uploaderId,
		String originalFileName,
		String fileUrl,
		String mimeType,
		String fileType,
		Long fileSize,
		String checksum,
		String scanStatus,
		Instant createdAt) {
	public static AttachmentUploadResponse from(MessageAttachment attachment) {
		return new AttachmentUploadResponse(
				attachment.id(),
				attachment.conversationId(),
				attachment.uploaderId(),
				attachment.originalFileName(),
				attachment.fileUrl(),
				attachment.mimeType(),
				attachment.fileType(),
				attachment.fileSize(),
				attachment.checksum(),
				attachment.scanStatus(),
				attachment.createdAt());
	}
}
