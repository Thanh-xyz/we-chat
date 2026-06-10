package main.com.chat.wechat.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
		@NotBlank
		String token) {
}
