package main.com.chat.wechat.notification.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class NotificationEventPublisher {
	private final ApplicationEventPublisher applicationEventPublisher;

	public NotificationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	public void publish(NotificationEvent event) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			applicationEventPublisher.publishEvent(event);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				applicationEventPublisher.publishEvent(event);
			}
		});
	}
}
