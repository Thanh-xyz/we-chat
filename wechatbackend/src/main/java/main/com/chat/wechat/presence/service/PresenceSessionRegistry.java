package main.com.chat.wechat.presence.service;

import java.util.UUID;

/**
 * Tracks live WebSocket sessions per user so presence survives multiple tabs/devices.
 */
public interface PresenceSessionRegistry {
	/** Registers a session and returns the number of live sessions for the user. */
	int connect(UUID userId, String sessionId);

	/** Removes a session and returns the number of live sessions left for the user. */
	int disconnect(UUID userId, String sessionId);

	boolean isOnline(UUID userId);

	/** Keeps sessions owned by this instance alive; no-op for purely local registries. */
	default void heartbeat() {
	}
}
