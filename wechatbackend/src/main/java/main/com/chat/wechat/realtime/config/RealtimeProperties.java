package main.com.chat.wechat.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
		boolean distributedEnabled,
		String channel,
		String instanceId,
		int maxEventBytes,
		int publisherCoreSize,
		int publisherMaxSize,
		int publisherQueueCapacity,
		int listenerCoreSize,
		int listenerMaxSize,
		int listenerQueueCapacity,
		int dedupCacheSize,
		int shutdownTimeoutSeconds) {
	private static final Pattern CHANNEL_PATTERN = Pattern.compile("[A-Za-z0-9:_-]{1,128}");

	public RealtimeProperties {
		channel = channel == null || channel.isBlank() ? "webchat:local:realtime" : channel.trim();
		instanceId = instanceId == null || instanceId.isBlank() ? UUID.randomUUID().toString() : instanceId.trim();
		if (!CHANNEL_PATTERN.matcher(channel).matches()) {
			throw new IllegalArgumentException("app.realtime.channel must contain only safe Redis channel characters");
		}
		if (maxEventBytes < 1_024 || maxEventBytes > 1_048_576) {
			throw new IllegalArgumentException("app.realtime.max-event-bytes must be between 1024 and 1048576");
		}
		validatePool("publisher", publisherCoreSize, publisherMaxSize, publisherQueueCapacity);
		validatePool("listener", listenerCoreSize, listenerMaxSize, listenerQueueCapacity);
		if (dedupCacheSize < 100 || dedupCacheSize > 1_000_000) {
			throw new IllegalArgumentException("app.realtime.dedup-cache-size must be between 100 and 1000000");
		}
		if (shutdownTimeoutSeconds < 1 || shutdownTimeoutSeconds > 300) {
			throw new IllegalArgumentException("app.realtime.shutdown-timeout-seconds must be between 1 and 300");
		}
	}

	private static void validatePool(String name, int coreSize, int maxSize, int queueCapacity) {
		if (coreSize < 1 || maxSize < coreSize || queueCapacity < 1) {
			throw new IllegalArgumentException("app.realtime." + name + " pool sizes and queue capacity must be positive and ordered");
		}
	}
}
