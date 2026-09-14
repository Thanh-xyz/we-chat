package main.com.chat.wechat.notification.dispatcher;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import main.com.chat.wechat.notification.event.NotificationEvent;
import main.com.chat.wechat.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
public class InProcessNotificationDispatcher implements NotificationDispatcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(InProcessNotificationDispatcher.class);

	private final Executor executor;
	private final NotificationService notificationService;
	private final MeterRegistry meterRegistry;

	public InProcessNotificationDispatcher(
			@Qualifier("notificationTaskExecutor") Executor executor,
			NotificationService notificationService,
			MeterRegistry meterRegistry) {
		this.executor = executor;
		this.notificationService = notificationService;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void dispatch(NotificationEvent event) {
		if (event == null) {
			return;
		}
		try {
			executor.execute(() -> dispatchOne(event));
		} catch (RejectedExecutionException exception) {
			counter("notification_dispatch_rejections_total", event.eventType()).increment();
			LOGGER.warn("Notification dispatch rejected eventType={}", event.eventType());
		}
	}

	private void dispatchOne(NotificationEvent event) {
		String eventType = event.eventType() == null ? "UNKNOWN" : event.eventType();
		Timer.Sample sample = Timer.start(meterRegistry);
		String result = "success";
		try {
			notificationService.handleNotificationEvent(event);
			counter("notification_dispatch_total", eventType, result).increment();
		} catch (RuntimeException exception) {
			result = "failure";
			counter("notification_dispatch_total", eventType, result).increment();
			LOGGER.warn("Notification dispatch failed eventType={} exceptionClass={}", eventType, exception.getClass().getName());
		} finally {
			sample.stop(timer(eventType, result));
		}
	}

	private Counter counter(String name, String eventType) {
		return counter(name, eventType, null);
	}

	private Counter counter(String name, String eventType, String result) {
		var builder = Counter.builder(name).tag("type", eventType == null ? "UNKNOWN" : eventType);
		if (result != null) {
			builder = builder.tag("result", result);
		}
		return builder.register(meterRegistry);
	}

	private Timer timer(String eventType, String result) {
		return Timer.builder("notification_dispatch_duration_seconds")
				.tag("type", eventType)
				.tag("result", result)
				.publishPercentileHistogram(false)
				.register(meterRegistry);
	}
}
