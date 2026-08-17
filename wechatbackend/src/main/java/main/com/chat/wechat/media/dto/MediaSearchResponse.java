package main.com.chat.wechat.media.dto;

import main.com.chat.wechat.media.model.MediaItem;

import java.util.List;

public record MediaSearchResponse(
		String fileName,
		String category,
		List<MediaResponse> items,
		int limit,
		int offset,
		boolean hasMore) {

	public static MediaSearchResponse from(String fileName, String category, List<MediaItem> items, int limit, int offset) {
		List<MediaResponse> visibleItems = items.stream()
				.limit(limit)
				.map(MediaResponse::from)
				.toList();
		return new MediaSearchResponse(fileName, category, visibleItems, limit, offset, items.size() > limit);
	}
}
