package main.com.chat.wechat.notification.event;

import main.com.chat.wechat.notification.dispatcher.NotificationDispatcher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {
	private final NotificationDispatcher notificationDispatcher;

	public NotificationEventListener(NotificationDispatcher notificationDispatcher) {
		this.notificationDispatcher = notificationDispatcher;
	}

	@EventListener
	public void onNotificationEvent(NotificationEvent event) {
		notificationDispatcher.dispatch(event);
	}
}
