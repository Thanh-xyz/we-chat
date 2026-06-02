package main.com.chat.wechat.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@Size(min = 1, max = 120)
		String displayName,

		@Size(max = 2048)
		String avatarUrl) {
}
