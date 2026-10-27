package main.com.chat.wechat.message.dto;

import main.com.chat.wechat.user.model.User;

import java.util.UUID;

public record MessageSenderResponse(
		UUID userId,
		String username,
		String displayName,
		String avatarUrl) {

	public static MessageSenderResponse from(User user) {
		return user == null ? null : new MessageSenderResponse(
				user.id(),
				user.username(),
				user.displayName(),
				user.avatarUrl());
	}
}
