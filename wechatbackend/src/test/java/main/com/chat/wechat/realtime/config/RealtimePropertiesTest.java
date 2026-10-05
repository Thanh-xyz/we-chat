package main.com.chat.wechat.realtime.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimePropertiesTest {
	@Test
	void unsafeChannelIsRejected() {
		assertThatThrownBy(() -> new RealtimeProperties(
				true, "webchat channel", "instance-a", 262144,
				1, 2, 100, 1, 2, 100, 1000, 10))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void poolAndPayloadBoundsAreRejected() {
		assertThatThrownBy(() -> new RealtimeProperties(
				true, "webchat:realtime", "instance-a", 512,
				1, 2, 100, 1, 2, 100, 1000, 10))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RealtimeProperties(
				true, "webchat:realtime", "instance-a", 262144,
				2, 1, 100, 1, 2, 100, 1000, 10))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
