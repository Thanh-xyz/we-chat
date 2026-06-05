package main.com.chat.wechat.realtime.security;

import java.security.Principal;
import java.util.UUID;

public record WebSocketUserPrincipal(UUID userId, String username) implements Principal {
	@Override
	public String getName() {
		return userId.toString();
	}
}
