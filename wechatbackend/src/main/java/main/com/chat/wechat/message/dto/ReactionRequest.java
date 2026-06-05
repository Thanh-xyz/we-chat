package main.com.chat.wechat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactionRequest(
		@NotBlank
		@Size(max = 40)
		String emoji) {
}
