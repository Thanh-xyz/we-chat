package main.com.chat.wechat.friendship.dto;

import jakarta.validation.constraints.Size;

public record BlockUserRequest(
		@Size(max = 255)
		String reason) {
}
