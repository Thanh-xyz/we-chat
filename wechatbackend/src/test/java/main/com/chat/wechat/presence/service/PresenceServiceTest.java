package main.com.chat.wechat.presence.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import main.com.chat.wechat.presence.repository.PresenceRepository;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.security.WebSocketUserPrincipal;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceServiceTest {
	private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private final UUID friendId = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private final WebSocketUserPrincipal principal = new WebSocketUserPrincipal(userId, "thanh");

	private PresenceRepository repository;
	private RealtimeEventPublisher publisher;
	private PresenceService service;

	@BeforeEach
	void setUp() {
		repository = mock(PresenceRepository.class);
		publisher = mock(RealtimeEventPublisher.class);
		service = new PresenceService(new LocalPresenceSessionRegistry(), repository, publisher);
		when(repository.findAudience(userId)).thenReturn(List.of(friendId));
	}

	@Test
	void firstSessionMarksUserOnlineAndNotifiesAudience() {
		when(repository.updateStatus(userId, PresenceStatus.ONLINE)).thenReturn(true);

		service.onConnected(connected("s1"));

		ArgumentCaptor<RealtimeEvent> event = ArgumentCaptor.forClass(RealtimeEvent.class);
		verify(publisher).publishToMembersAfterCommit(any(), event.capture());
		assertThat(event.getValue().type()).isEqualTo("presence.updated");
		assertThat(event.getValue().actorUserId()).isEqualTo(userId);
		assertThat(event.getValue().payload()).isEqualTo(Map.of("status", "ONLINE"));
	}

	@Test
	void userStaysOnlineUntilTheLastTabDisconnects() {
		when(repository.updateStatus(any(), any())).thenReturn(true);
		service.onConnected(connected("s1"));
		service.onConnected(connected("s2"));

		service.onDisconnected(disconnected("s1"));
		verify(repository, never()).updateStatus(userId, PresenceStatus.OFFLINE);

		service.onDisconnected(disconnected("s2"));
		verify(repository).updateStatus(userId, PresenceStatus.OFFLINE);
	}

	@Test
	void unchangedStatusDoesNotBroadcast() {
		when(repository.updateStatus(userId, PresenceStatus.ONLINE)).thenReturn(false);

		service.onConnected(connected("s1"));

		verify(publisher, never()).publishToMembersAfterCommit(anyCollection(), any());
	}

	@Test
	void sweepClearsStaleOnlineUsersWithoutLiveSessions() {
		when(repository.findUserIdsWithStatus(PresenceStatus.ONLINE)).thenReturn(List.of(userId));
		when(repository.updateStatus(userId, PresenceStatus.OFFLINE)).thenReturn(true);

		service.heartbeatAndSweep();

		verify(repository).updateStatus(userId, PresenceStatus.OFFLINE);
		verify(publisher).publishToMembersAfterCommit(any(), any());
	}

	@Test
	void sweepKeepsUsersWithLiveSessions() {
		when(repository.updateStatus(any(), any())).thenReturn(true);
		service.onConnected(connected("s1"));
		when(repository.findUserIdsWithStatus(PresenceStatus.ONLINE)).thenReturn(List.of(userId));

		service.heartbeatAndSweep();

		verify(repository, never()).updateStatus(userId, PresenceStatus.OFFLINE);
	}

	private SessionConnectedEvent connected(String sessionId) {
		return new SessionConnectedEvent(this, message(sessionId), principal);
	}

	private SessionDisconnectEvent disconnected(String sessionId) {
		return new SessionDisconnectEvent(this, message(sessionId), sessionId, CloseStatus.NORMAL, principal);
	}

	private Message<byte[]> message(String sessionId) {
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK);
		accessor.setSessionId(sessionId);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
