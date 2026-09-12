package main.com.chat.wechat.media.dto;

import main.com.chat.wechat.media.model.MediaItem;

import java.util.UUID;

public record MediaUploaderResponse(
		UUID userId,
		String username,
		String displayName,
		String avatarUrl) {

	public static MediaUploaderResponse from(MediaItem item) {
		return new MediaUploaderResponse(
				item.uploaderId(),
				item.uploaderUsername(),
				item.uploaderDisplayName(),
				item.uploaderAvatarUrl());
	}
}
