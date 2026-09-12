package main.com.chat.wechat.media.dto;

import main.com.chat.wechat.media.model.MediaItem;

import java.util.List;

public record MediaGalleryResponse(
		List<MediaResponse> items,
		int limit,
		int offset,
		boolean hasMore) {

	public static MediaGalleryResponse from(List<MediaItem> items, int limit, int offset) {
		List<MediaResponse> visibleItems = items.stream()
				.limit(limit)
				.map(MediaResponse::from)
				.toList();
		return new MediaGalleryResponse(visibleItems, limit, offset, items.size() > limit);
	}
}
