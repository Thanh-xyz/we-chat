package main.com.chat.wechat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(
		@NotBlank
		@Size(max = 10000)
		String content) {
}
