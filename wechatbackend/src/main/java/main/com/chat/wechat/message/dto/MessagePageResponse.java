package main.com.chat.wechat.message.dto;

import java.util.List;

public record MessagePageResponse(
		List<MessageResponse> items,
		String nextCursor,
		boolean hasNext,
		int limit) {
}

