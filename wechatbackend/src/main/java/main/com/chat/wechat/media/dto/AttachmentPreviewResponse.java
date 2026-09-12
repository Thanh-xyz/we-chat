package main.com.chat.wechat.media.dto;

import main.com.chat.wechat.media.model.MediaCategory;
import main.com.chat.wechat.media.model.MediaItem;

import java.time.Instant;
import java.util.UUID;

public record AttachmentPreviewResponse(
		UUID id,
		MediaCategory category,
		String fileName,
		String mimeType,
		Long fileSize,
		String previewUrl,
		String downloadUrl,
		boolean inlinePreview,
		Integer width,
		Integer height,
		Integer durationSeconds,
		Instant createdAt) {

	public static AttachmentPreviewResponse from(MediaItem item) {
		return new AttachmentPreviewResponse(
				item.id(),
				item.category(),
				item.fileName(),
				item.mimeType(),
				item.fileSize(),
				"/api/media/" + item.id() + "/preview",
				"/api/media/" + item.id() + "/download",
				canPreviewInline(item),
				item.width(),
				item.height(),
				item.durationSeconds(),
				item.createdAt());
	}

	private static boolean canPreviewInline(MediaItem item) {
		if (item.category() == MediaCategory.IMAGE || item.category() == MediaCategory.VOICE || item.category() == MediaCategory.VIDEO) {
			return true;
		}
		String mimeType = item.mimeType();
		return "application/pdf".equals(mimeType) || "text/plain".equals(mimeType);
	}
}
