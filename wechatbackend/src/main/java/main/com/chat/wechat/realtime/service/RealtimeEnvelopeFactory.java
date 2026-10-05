package main.com.chat.wechat.realtime.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import main.com.chat.wechat.realtime.config.RealtimeProperties;
import main.com.chat.wechat.realtime.dto.RealtimeEnvelope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RealtimeEnvelopeFactory {
	private final ObjectMapper objectMapper;
	private final RealtimeProperties properties;
	private final RealtimeDestinationPolicy destinationPolicy;

	public RealtimeEnvelopeFactory(
			ObjectMapper objectMapper,
			RealtimeProperties properties,
			RealtimeDestinationPolicy destinationPolicy) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.destinationPolicy = destinationPolicy;
	}

	public RealtimeEnvelope create(String destination, String eventType, Object payload) {
		JsonNode jsonPayload = objectMapper.valueToTree(payload);
		RealtimeEnvelope envelope = new RealtimeEnvelope(
				RealtimeEnvelope.CURRENT_VERSION,
				UUID.randomUUID(),
				properties.instanceId(),
				destination,
				eventType,
				jsonPayload,
				Instant.now());
		destinationPolicy.validate(envelope);
		return envelope;
	}

	public byte[] serialize(RealtimeEnvelope envelope) {
		try {
			byte[] bytes = objectMapper.writeValueAsBytes(envelope);
			if (bytes.length > properties.maxEventBytes()) {
				throw new IllegalArgumentException("Realtime event exceeds configured size limit");
			}
			return bytes;
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("Realtime event serialization failed", exception);
		}
	}

	public RealtimeEnvelope deserialize(byte[] bytes) {
		if (bytes == null || bytes.length == 0 || bytes.length > properties.maxEventBytes()) {
			throw new IllegalArgumentException("Realtime event exceeds configured size limit");
		}
		try {
			RealtimeEnvelope envelope = objectMapper.readValue(bytes, RealtimeEnvelope.class);
			destinationPolicy.validate(envelope);
			return envelope;
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("Malformed realtime event", exception);
		}
	}
}
