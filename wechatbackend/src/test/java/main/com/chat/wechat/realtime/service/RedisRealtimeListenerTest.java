package main.com.chat.wechat.realtime.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import main.com.chat.wechat.realtime.config.RealtimeProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RedisRealtimeListenerTest {
	@Test
	void malformedAndDuplicateEventsNeverReachLocalBroker() {
		RealtimeProperties properties = properties("listener-a");
		RealtimeDestinationPolicy policy = new RealtimeDestinationPolicy();
		RealtimeEnvelopeFactory factory = new RealtimeEnvelopeFactory(new ObjectMapper(), properties, policy);
		LocalRealtimeDelivery delivery = Mockito.mock(LocalRealtimeDelivery.class);
		RedisRealtimeListener listener = new RedisRealtimeListener(
				properties, factory, policy, delivery, new SimpleMeterRegistry());

		Message malformed = Mockito.mock(Message.class);
		Mockito.when(malformed.getBody()).thenReturn("{\"not\":\"an-envelope\"}".getBytes());
		listener.onMessage(malformed, null);

		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000003");
		var envelope = factory.create(policy.userTopic(userId), "message.created", Map.of("id", "m1"));
		Message valid = Mockito.mock(Message.class);
		Mockito.when(valid.getBody()).thenReturn(factory.serialize(envelope));
		listener.onMessage(valid, null);
		listener.onMessage(valid, null);

		verify(delivery, times(1)).deliver(envelope);
	}

	private RealtimeProperties properties(String instanceId) {
		return new RealtimeProperties(
				true, "webchat:test:realtime", instanceId, 262144,
				1, 2, 100, 1, 2, 100, 1000, 10);
	}
}
