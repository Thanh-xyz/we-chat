package main.com.chat.wechat.common.security;

import java.util.UUID;

public record AuthState(UUID userId, int tokenVersion, boolean active) {
	public AuthState {
		if (userId == null) {
			throw new IllegalArgumentException("Auth state userId is required");
		}
		if (tokenVersion < 0) {
			throw new IllegalArgumentException("Auth state tokenVersion must be non-negative");
		}
	}
}
