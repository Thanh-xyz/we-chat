package main.com.chat.wechat.conversation.dto;

import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(
		@Size(max = 100)
		String name,

		@Size(max = 2048)
		String avatarUrl) {
}
