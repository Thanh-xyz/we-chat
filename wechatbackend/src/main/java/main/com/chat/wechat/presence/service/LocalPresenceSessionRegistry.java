package main.com.chat.wechat.presence.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Single-instance registry used when distributed realtime (Redis) is disabled. */
@Component
@ConditionalOnProperty(name = "app.realtime.distributed-enabled", havingValue = "false", matchIfMissing = true)
public class LocalPresenceSessionRegistry implements PresenceSessionRegistry {
	private final Map<UUID, Set<String>> sessions = new ConcurrentHashMap<>();

	@Override
	public int connect(UUID userId, String sessionId) {
		Set<String> userSessions = sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
		userSessions.add(sessionId);
		return userSessions.size();
	}

	@Override
	public int disconnect(UUID userId, String sessionId) {
		Set<String> remaining = sessions.computeIfPresent(userId, (ignored, userSessions) -> {
			userSessions.remove(sessionId);
			return userSessions.isEmpty() ? null : userSessions;
		});
		return remaining == null ? 0 : remaining.size();
	}

	@Override
	public boolean isOnline(UUID userId) {
		Set<String> userSessions = sessions.get(userId);
		return userSessions != null && !userSessions.isEmpty();
	}
}
