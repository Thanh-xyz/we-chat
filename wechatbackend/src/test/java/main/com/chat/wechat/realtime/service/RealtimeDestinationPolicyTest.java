package main.com.chat.wechat.realtime.service;

import main.com.chat.wechat.realtime.dto.RealtimeEnvelope;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeDestinationPolicyTest {
	private final RealtimeDestinationPolicy policy = new RealtimeDestinationPolicy();
	private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	void onlyKnownBrowserDestinationsAreAccepted() {
		assertThat(policy.parse(policy.userQueue(userId)).kind())
				.isEqualTo(RealtimeDestinationPolicy.Kind.USER_QUEUE);
		assertThat(policy.parse(policy.notificationTopic(userId)).kind())
				.isEqualTo(RealtimeDestinationPolicy.Kind.NOTIFICATION_TOPIC);
		assertThat(policy.parse(policy.userTopic(userId)).kind())
				.isEqualTo(RealtimeDestinationPolicy.Kind.USER_TOPIC);
	}

	@Test
	void unknownDestinationAndEventTypeAreRejected() {
		assertThatThrownBy(() -> policy.parse("/topic/all-users"))
				.isInstanceOf(IllegalArgumentException.class);

		RealtimeEnvelope envelope = new RealtimeEnvelope(
				RealtimeEnvelope.CURRENT_VERSION,
				UUID.randomUUID(),
				"instance-a",
				policy.userTopic(userId),
				"unknown.event",
				JsonNodeFactory.instance.objectNode(),
				Instant.now());

		assertThatThrownBy(() -> policy.validate(envelope))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
