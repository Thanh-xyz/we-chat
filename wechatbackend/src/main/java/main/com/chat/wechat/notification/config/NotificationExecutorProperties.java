package main.com.chat.wechat.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.notification.executor")
public record NotificationExecutorProperties(
		int coreSize,
		int maxSize,
		int queueCapacity,
		int batchSize,
		Duration shutdownTimeout) {
	public NotificationExecutorProperties {
		if (coreSize < 1) {
			throw new IllegalArgumentException("app.notification.executor.core-size must be at least 1");
		}
		if (maxSize < coreSize) {
			throw new IllegalArgumentException("app.notification.executor.max-size must be greater than or equal to core-size");
		}
		if (queueCapacity < 1) {
			throw new IllegalArgumentException("app.notification.executor.queue-capacity must be at least 1");
		}
		if (batchSize < 1 || batchSize > 10_000) {
			throw new IllegalArgumentException("app.notification.executor.batch-size must be between 1 and 10000");
		}
		if (shutdownTimeout == null || shutdownTimeout.isZero() || shutdownTimeout.isNegative()) {
			throw new IllegalArgumentException("app.notification.executor.shutdown-timeout must be positive");
		}
	}
}
