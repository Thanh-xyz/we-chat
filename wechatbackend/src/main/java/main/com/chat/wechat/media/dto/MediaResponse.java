package main.com.chat.wechat.media.dto;

import main.com.chat.wechat.media.model.MediaCategory;
import main.com.chat.wechat.media.model.MediaItem;

import java.time.Instant;
import java.util.UUID;

public record MediaResponse(
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
		Long downloadCount,
		Instant createdAt) {

	public static MediaResponse from(MediaItem item) {
		String downloadUrl = "/api/media/" + item.id() + "/download";
		String previewUrl = "/api/media/" + item.id() + "/preview";
		String thumbnailUrl = item.category() == MediaCategory.IMAGE ? previewUrl : null;
		return new MediaResponse(
				item.id(),
				item.messageId(),
				item.conversationId(),
				item.category(),
				item.fileName(),
				item.mimeType(),
				item.fileSize(),
				MediaUploaderResponse.from(item),
				downloadUrl,
				previewUrl,
				downloadUrl,
				thumbnailUrl,
				item.width(),
				item.height(),
				item.durationSeconds(),
				item.downloadCount(),
				item.createdAt());
	}
}
