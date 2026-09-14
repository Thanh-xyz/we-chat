package main.com.chat.wechat.notification.dispatcher;

import main.com.chat.wechat.notification.event.NotificationEvent;

public interface NotificationDispatcher {
	void dispatch(NotificationEvent event);
}
