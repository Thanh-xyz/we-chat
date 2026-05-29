package main.com.chat.wechat.auth.dto;

import main.com.chat.wechat.user.model.User;

import java.util.UUID;

public record AuthUserResponse(
		UUID id,
		String username,
		String email,
		String displayName,
		String avatarUrl,
		String status,
		String role) {

	public static AuthUserResponse from(User user) {
		return new AuthUserResponse(
				user.id(),
				user.username(),
				user.email(),
				user.displayName(),
				user.avatarUrl(),
				user.status(),
				user.role());
	}
}
