package main.com.chat.wechat.media.model;

import java.time.Instant;
import java.util.UUID;

public record MediaItem(
		UUID id,
		UUID messageId,
		UUID conversationId,
		UUID uploaderId,
		String uploaderUsername,
		String uploaderDisplayName,
		String uploaderAvatarUrl,
		String fileName,
		String storageKey,
		String fileUrl,
		String mimeType,
		MediaCategory category,
		Long fileSize,
		Integer width,
		Integer height,
		Integer durationSeconds,
		String checksum,
		String scanStatus,
		Long downloadCount,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt) {
}
