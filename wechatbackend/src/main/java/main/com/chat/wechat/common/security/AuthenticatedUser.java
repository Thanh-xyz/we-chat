package main.com.chat.wechat.common.security;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
		UUID id,
		String username,
		String email,
		List<String> roles,
		List<String> permissions,
		int tokenVersion) {
}
