package main.com.chat.wechat.media.dto;

import main.com.chat.wechat.media.model.MediaCategory;
import main.com.chat.wechat.media.model.MediaItem;

import java.time.Instant;
import java.util.UUID;

public record MediaDetailResponse(
		UUID id,
		UUID messageId,
		UUID conversationId,
		MediaCategory category,
		String fileName,
		String mimeType,
		Long fileSize,
		MediaUploaderResponse uploadedBy,
		String originalUrl,
		String previewUrl,
		String downloadUrl,
		String thumbnailUrl,
		Integer width,
		Integer height,
		Integer durationSeconds,
		String checksum,
		String scanStatus,
		Long downloadCount,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt) {

	public static MediaDetailResponse from(MediaItem item) {
		MediaResponse media = MediaResponse.from(item);
		return new MediaDetailResponse(
				media.id(),
				media.messageId(),
				media.conversationId(),
				media.category(),
				media.fileName(),
				media.mimeType(),
				media.fileSize(),
				media.uploadedBy(),
				media.originalUrl(),
				media.previewUrl(),
				media.downloadUrl(),
				media.thumbnailUrl(),
				media.width(),
				media.height(),
				media.durationSeconds(),
				item.checksum(),
				item.scanStatus(),
				item.downloadCount(),
				item.deletedAt(),
				item.createdAt(),
				item.updatedAt());
	}
}
