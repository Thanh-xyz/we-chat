package main.com.chat.wechat.realtime.service;

import main.com.chat.wechat.realtime.config.RealtimeProperties;
import main.com.chat.wechat.realtime.dto.RealtimeEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

public class RedisRealtimeListener implements MessageListener {
	private static final Logger log = LoggerFactory.getLogger(RedisRealtimeListener.class);

	private final RealtimeProperties properties;
	private final RealtimeEnvelopeFactory envelopeFactory;
	private final RealtimeDestinationPolicy destinationPolicy;
	private final LocalRealtimeDelivery localRealtimeDelivery;
	private final RecentRealtimeEventIds recentEventIds;
	private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

	public RedisRealtimeListener(
			RealtimeProperties properties,
			RealtimeEnvelopeFactory envelopeFactory,
			RealtimeDestinationPolicy destinationPolicy,
			LocalRealtimeDelivery localRealtimeDelivery,
			io.micrometer.core.instrument.MeterRegistry meterRegistry) {
		this.properties = properties;
		this.envelopeFactory = envelopeFactory;
		this.destinationPolicy = destinationPolicy;
		this.localRealtimeDelivery = localRealtimeDelivery;
		this.recentEventIds = new RecentRealtimeEventIds(properties.dedupCacheSize());
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		if (message == null || message.getBody() == null || message.getBody().length > properties.maxEventBytes()) {
			counter("realtime_redis_receive_total", "unknown", "dropped").increment();
			counter("realtime_redis_dropped_total", "unknown", "oversized_or_empty").increment();
			log.warn("Redis realtime event dropped reason=oversized_or_empty instanceId={}", properties.instanceId());
			return;
		}
		RealtimeEnvelope envelope;
		try {
			envelope = envelopeFactory.deserialize(message.getBody());
		} catch (RuntimeException exception) {
			counter("realtime_redis_receive_total", "unknown", "invalid").increment();
			counter("realtime_redis_receive_failures_total", "unknown", "invalid").increment();
			log.warn("Redis realtime event rejected instanceId={} exceptionClass={}", properties.instanceId(), exception.getClass().getSimpleName());
			return;
		}
		counter("realtime_redis_receive_total", envelope.eventType(), "accepted").increment();
		if (!recentEventIds.markIfNew(envelope.eventId())) {
			counter("realtime_redis_duplicate_total", envelope.eventType(), "duplicate").increment();
			return;
		}
		try {
			localRealtimeDelivery.deliver(envelope);
			counter("realtime_redis_delivery_total", envelope.eventType(), "success").increment();
		} catch (RuntimeException exception) {
			counter("realtime_redis_delivery_failures_total", envelope.eventType(), "failure").increment();
			log.warn("Redis realtime local delivery failed eventType={} category={} instanceId={} exceptionClass={}",
					envelope.eventType(), destinationPolicy.category(envelope.destination()), properties.instanceId(), exception.getClass().getSimpleName());
		}
	}

	private io.micrometer.core.instrument.Counter counter(String name, String eventType, String result) {
		return io.micrometer.core.instrument.Counter.builder(name)
				.tag("event_type", eventType == null || eventType.isBlank() ? "unknown" : eventType)
				.tag("result", result)
				.register(meterRegistry);
	}
}
