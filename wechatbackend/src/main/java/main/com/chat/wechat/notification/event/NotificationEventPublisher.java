package main.com.chat.wechat.notification.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventPublisher {
	private final ApplicationEventPublisher applicationEventPublisher;

	public NotificationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	public void publish(NotificationEvent event) {
		applicationEventPublisher.publishEvent(event);
	}
}
