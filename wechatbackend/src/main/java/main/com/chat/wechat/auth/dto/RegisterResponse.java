package main.com.chat.wechat.auth.dto;

import java.util.UUID;

public record RegisterResponse(
		UUID id,
		String email,
		boolean emailVerified) {
}
