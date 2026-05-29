package main.com.chat.wechat.common.security;

import java.util.UUID;

public record AuthenticatedUser(
		UUID id,
		String username,
		String email,
		String role) {
}
