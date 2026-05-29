package main.com.chat.wechat.common.security;

import java.util.UUID;

public record JwtClaims(
		UUID userId,
		String username,
		String email,
		String role) {
}
