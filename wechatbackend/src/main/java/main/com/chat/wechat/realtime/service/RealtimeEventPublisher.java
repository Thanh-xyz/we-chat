package main.com.chat.wechat.realtime.service;

import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.notification.dto.NotificationRealtimeEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.UUID;

@Service
public class RealtimeEventPublisher {
	private final RealtimeBroker realtimeBroker;
	private final RealtimeDestinationPolicy destinationPolicy;

	public RealtimeEventPublisher(
			RealtimeBroker realtimeBroker,
			RealtimeDestinationPolicy destinationPolicy) {
		this.realtimeBroker = realtimeBroker;
		this.destinationPolicy = destinationPolicy;
	}

	public void publishToMembersAfterCommit(Collection<UUID> memberIds, RealtimeEvent event) {
		runAfterCommit(() -> memberIds.stream()
				.distinct()
				.forEach(userId -> realtimeBroker.publish(
						destinationPolicy.userQueue(userId),
						event.type(),
						event)));
	}

	public void publishToUserAfterCommit(UUID userId, RealtimeEvent event) {
		runAfterCommit(() -> realtimeBroker.publish(
				destinationPolicy.userQueue(userId),
				event.type(),
				event));
	}

	public void publishNotificationToUserAfterCommit(UUID userId, Object event) {
		runAfterCommit(() -> realtimeBroker.publish(
				destinationPolicy.notificationTopic(userId),
				eventType(event),
				event));
	}

	public void publishUserTopicAfterCommit(UUID userId, Object event) {
		runAfterCommit(() -> realtimeBroker.publish(
				destinationPolicy.userTopic(userId),
				eventType(event),
				event));
	}

	private String eventType(Object event) {
		if (event instanceof RealtimeEvent realtimeEvent) {
			return realtimeEvent.type();
		}
		if (event instanceof NotificationRealtimeEvent notificationEvent) {
			return notificationEvent.eventType();
		}
		throw new IllegalArgumentException("Unsupported realtime event payload");
	}

	private void runAfterCommit(Runnable runnable) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			runnable.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				runnable.run();
			}
		});
	}
}
