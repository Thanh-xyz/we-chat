package main.com.chat.wechat.realtime.service;

import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.UUID;

@Service
public class RealtimeEventPublisher {
	private static final String USER_CONVERSATION_EVENTS_DESTINATION = "/queue/conversation-events";

	private final SimpMessagingTemplate messagingTemplate;

	public RealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	public void publishToMembersAfterCommit(Collection<UUID> memberIds, RealtimeEvent event) {
		runAfterCommit(() -> memberIds.stream()
				.distinct()
				.forEach(userId -> messagingTemplate.convertAndSendToUser(
						userId.toString(),
						USER_CONVERSATION_EVENTS_DESTINATION,
						event)));
	}

	public void publishToUserAfterCommit(UUID userId, RealtimeEvent event) {
		runAfterCommit(() -> messagingTemplate.convertAndSendToUser(
				userId.toString(),
				USER_CONVERSATION_EVENTS_DESTINATION,
				event));
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
