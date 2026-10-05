package main.com.chat.wechat.realtime.service;

import main.com.chat.wechat.notification.dto.NotificationRealtimeEvent;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RealtimeEventPublisherTest {
	private final RealtimeDestinationPolicy policy = new RealtimeDestinationPolicy();
	private final RealtimeBroker broker = mock(RealtimeBroker.class);
	private final RealtimeEventPublisher publisher = new RealtimeEventPublisher(broker, policy);
	private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void publishesKnownDestinationAfterCommitAndDeduplicatesMembers() {
		RealtimeEvent event = RealtimeEvent.of("message.created", null, null, userId, null, Map.of());
		TransactionSynchronizationManager.initSynchronization();
		try {
			publisher.publishToMembersAfterCommit(List.of(userId, userId), event);
			verifyNoInteractions(broker);
			for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
				synchronization.afterCommit();
			}
			verify(broker, times(1)).publish(eq(policy.userQueue(userId)), eq("message.created"), same(event));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void mapsNotificationPayloadToNotificationTopic() {
		NotificationRealtimeEvent event = NotificationRealtimeEvent.of(
				"notification.created", UUID.randomUUID(), userId, 1, Map.of("kind", "friend"));
		publisher.publishNotificationToUserAfterCommit(userId, event);

		verify(broker).publish(eq(policy.notificationTopic(userId)), eq("notification.created"), same(event));
	}
}
