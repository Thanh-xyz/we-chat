package main.com.chat.wechat.realtime.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import main.com.chat.wechat.realtime.config.RealtimeProperties;
import main.com.chat.wechat.realtime.dto.RealtimeEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;

public class RedisRealtimeBroker implements RealtimeBroker {
	private static final Logger log = LoggerFactory.getLogger(RedisRealtimeBroker.class);

	private final StringRedisTemplate redisTemplate;
	private final RealtimeProperties properties;
	private final RealtimeEnvelopeFactory envelopeFactory;
	private final TaskExecutor publisherExecutor;
	private final MeterRegistry meterRegistry;

	public RedisRealtimeBroker(
			StringRedisTemplate redisTemplate,
			RealtimeProperties properties,
			RealtimeEnvelopeFactory envelopeFactory,
			TaskExecutor publisherExecutor,
			MeterRegistry meterRegistry) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.envelopeFactory = envelopeFactory;
		this.publisherExecutor = publisherExecutor;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void publish(String destination, String eventType, Object payload) {
		RealtimeEnvelope envelope;
		try {
			envelope = envelopeFactory.create(destination, eventType, payload);
			byte[] serialized = envelopeFactory.serialize(envelope);
			publisherExecutor.execute(() -> publishSerialized(envelope, serialized));
		} catch (RejectedExecutionException exception) {
			counter("realtime_redis_publish_failures_total", safeEventType(eventType), "rejected").increment();
			log.warn("Redis realtime publish rejected eventType={} instanceId={} exceptionClass={}", safeEventType(eventType), properties.instanceId(), exception.getClass().getSimpleName());
		} catch (Exception exception) {
			counter("realtime_redis_publish_failures_total", safeEventType(eventType), "failure").increment();
			log.warn("Redis realtime publish failed eventType={} instanceId={} exceptionClass={}", safeEventType(eventType), properties.instanceId(), exception.getClass().getSimpleName());
		}
	}

	private void publishSerialized(RealtimeEnvelope envelope, byte[] serialized) {
		try {
			redisTemplate.convertAndSend(properties.channel(), new String(serialized, StandardCharsets.UTF_8));
			counter("realtime_redis_publish_total", envelope.eventType(), "success").increment();
			distributionSummary(envelope.eventType()).record(serialized.length);
		} catch (Exception exception) {
			counter("realtime_redis_publish_failures_total", envelope.eventType(), "failure").increment();
			log.warn("Redis realtime publish failed eventType={} instanceId={} exceptionClass={}", safeEventType(envelope.eventType()), properties.instanceId(), exception.getClass().getSimpleName());
		}
	}

	private Counter counter(String name, String eventType, String result) {
		return Counter.builder(name)
				.tag("event_type", safeEventType(eventType))
				.tag("result", result)
				.register(meterRegistry);
	}

	private DistributionSummary distributionSummary(String eventType) {
		return DistributionSummary.builder("realtime_redis_event_size_bytes")
				.tag("event_type", safeEventType(eventType))
				.register(meterRegistry);
	}

	private String safeEventType(String eventType) {
		return eventType == null || eventType.isBlank() ? "unknown" : eventType;
	}
}
