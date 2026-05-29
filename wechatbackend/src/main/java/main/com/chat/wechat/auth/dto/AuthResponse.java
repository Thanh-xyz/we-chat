package main.com.chat.wechat.auth.dto;

import java.time.Instant;

public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		Instant accessTokenExpiresAt,
		Instant refreshTokenExpiresAt,
		AuthUserResponse user) {
}
