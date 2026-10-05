package main.com.chat.wechat.realtime.service;

import tools.jackson.databind.JsonNode;
import main.com.chat.wechat.realtime.dto.RealtimeEnvelope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LocalRealtimeDelivery {
	private static final String USER_CONVERSATION_EVENTS_DESTINATION = "/queue/conversation-events";

	private final SimpMessagingTemplate messagingTemplate;
	private final RealtimeDestinationPolicy destinationPolicy;

	public LocalRealtimeDelivery(
			SimpMessagingTemplate messagingTemplate,
			RealtimeDestinationPolicy destinationPolicy) {
		this.messagingTemplate = messagingTemplate;
		this.destinationPolicy = destinationPolicy;
	}

	public void deliver(RealtimeEnvelope envelope) {
		destinationPolicy.validate(envelope);
		RealtimeDestinationPolicy.Destination destination = destinationPolicy.parse(envelope.destination());
		JsonNode payload = envelope.payload();
		switch (destination.kind()) {
			case USER_QUEUE -> messagingTemplate.convertAndSendToUser(
					destination.userId().toString(),
					USER_CONVERSATION_EVENTS_DESTINATION,
					payload);
			case NOTIFICATION_TOPIC, USER_TOPIC -> messagingTemplate.convertAndSend(envelope.destination(), payload);
		}
	}
}
