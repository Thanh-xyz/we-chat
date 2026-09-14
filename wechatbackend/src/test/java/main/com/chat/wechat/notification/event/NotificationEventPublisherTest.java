package main.com.chat.wechat.notification.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationEventPublisherTest {
	private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

	@AfterEach
	void clearTransactionSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void publishesImmediatelyWhenThereIsNoTransaction() {
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		NotificationEventPublisher publisher = new NotificationEventPublisher(applicationEventPublisher);
		NotificationEvent event = event();

		publisher.publish(event);

		verify(applicationEventPublisher).publishEvent(event);
	}

	@Test
	void publishesOnlyAfterCommit() {
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		NotificationEventPublisher publisher = new NotificationEventPublisher(applicationEventPublisher);
		NotificationEvent event = event();
		TransactionSynchronizationManager.initSynchronization();

		publisher.publish(event);

		verifyNoInteractions(applicationEventPublisher);
		List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
		TransactionSynchronizationManager.clearSynchronization();
		synchronizations.forEach(TransactionSynchronization::afterCommit);

		verify(applicationEventPublisher).publishEvent(event);
	}

	@Test
	void doesNotPublishAfterRollback() {
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		NotificationEventPublisher publisher = new NotificationEventPublisher(applicationEventPublisher);
		TransactionSynchronizationManager.initSynchronization();

		publisher.publish(event());

		List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
		TransactionSynchronizationManager.clearSynchronization();
		synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		verifyNoInteractions(applicationEventPublisher);
	}

	private NotificationEvent event() {
		return NotificationEvent.messageCreated(ACTOR_ID, CONVERSATION_ID, MESSAGE_ID, "hello");
	}
}
