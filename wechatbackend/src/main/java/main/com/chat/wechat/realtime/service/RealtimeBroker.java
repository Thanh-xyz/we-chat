package main.com.chat.wechat.realtime.service;

public interface RealtimeBroker {
	void publish(String destination, String eventType, Object payload);
}
