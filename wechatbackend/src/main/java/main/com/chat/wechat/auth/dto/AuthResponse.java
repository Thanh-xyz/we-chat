package main.com.chat.wechat.auth.dto;

public record AuthResponse(
		String accessToken,
		String refreshToken,
		long expiresIn) {
}
