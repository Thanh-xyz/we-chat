package main.com.chat.wechat.presence.service;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import main.com.chat.wechat.presence.repository.PresenceRepository;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.security.WebSocketUserPrincipal;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Derives user presence from live STOMP sessions: ONLINE while at least one session is open,
 * OFFLINE when the last one closes. Transitions are persisted to users.status and pushed to
 * friends and conversation peers as {@code presence.updated} events.
 */
@Service
public class PresenceService {
	public static final String EVENT_TYPE = "presence.updated";
	private static final Logger LOGGER = LoggerFactory.getLogger(PresenceService.class);

	private final PresenceSessionRegistry registry;
	private final PresenceRepository presenceRepository;
	private final RealtimeEventPublisher realtimeEventPublisher;

	public PresenceService(
			PresenceSessionRegistry registry,
			PresenceRepository presenceRepository,
			RealtimeEventPublisher realtimeEventPublisher) {
		this.registry = registry;
		this.presenceRepository = presenceRepository;
		this.realtimeEventPublisher = realtimeEventPublisher;
	}

	@EventListener
	public void onConnected(SessionConnectedEvent event) {
		UUID userId = userId(event.getUser());
		String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
		if (userId == null || sessionId == null) {
			return;
		}
		registry.connect(userId, sessionId);
		changeStatus(userId, PresenceStatus.ONLINE);
	}

	@EventListener
	public void onDisconnected(SessionDisconnectEvent event) {
		UUID userId = userId(event.getUser());
		if (userId == null || event.getSessionId() == null) {
			return;
		}
		if (registry.disconnect(userId, event.getSessionId()) == 0) {
			changeStatus(userId, PresenceStatus.OFFLINE);
		}
	}

	/** Refreshes this instance's sessions and clears users whose sessions all expired (e.g. crashed node). */
	@Scheduled(fixedDelayString = "${app.presence.sweep-interval-ms:30000}", initialDelayString = "${app.presence.initial-delay-ms:15000}")
	public void heartbeatAndSweep() {
		try {
			registry.heartbeat();
			List<UUID> onlineUserIds = presenceRepository.findUserIdsWithStatus(PresenceStatus.ONLINE);
			for (UUID userId : onlineUserIds) {
				if (!registry.isOnline(userId)) {
					changeStatus(userId, PresenceStatus.OFFLINE);
				}
			}
		} catch (RuntimeException exception) {
			LOGGER.warn("Presence sweep failed: {}", exception.getClass().getSimpleName());
		}
	}

	void changeStatus(UUID userId, String status) {
		if (!presenceRepository.updateStatus(userId, status)) {
			return;
		}
		RealtimeEvent event = RealtimeEvent.of(EVENT_TYPE, null, null, userId, userId, Map.of("status", status));
		realtimeEventPublisher.publishToMembersAfterCommit(presenceRepository.findAudience(userId), event);
	}

	private static UUID userId(Principal principal) {
		return principal instanceof WebSocketUserPrincipal webSocketUser ? webSocketUser.userId() : null;
	}
}
