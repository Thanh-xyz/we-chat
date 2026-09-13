package main.com.chat.wechat.message.pagination;

import main.com.chat.wechat.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCursorCodecTest {
	private final MessageCursorCodec codec = new MessageCursorCodec();

	@Test
	void roundTripsTimestampAndId() {
		MessageCursor cursor = new MessageCursor(
				Instant.parse("2026-01-01T12:34:56.123456Z"),
				UUID.fromString("00000000-0000-0000-0000-000000000001"));

		assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
	}

	@Test
	void nullMeansFirstPage() {
		assertThat(codec.decode(null)).isNull();
	}

	@Test
	void rejectsMalformedAndOversizedValuesAsBadRequest() {
		assertInvalid("not-a-cursor");
		assertInvalid(encoded("v1|not-an-instant|00000000-0000-0000-0000-000000000001"));
		assertInvalid(encoded("v1|2026-01-01T00:00:00Z|not-a-uuid"));
		assertInvalid(encoded("v1|2026-01-01T00:00:00Z|00000000-0000-0000-0000-000000000001|extra"));
		assertInvalid("%");
		assertInvalid("A".repeat(257));
	}

	private void assertInvalid(String value) {
		assertThatThrownBy(() -> codec.decode(value))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getMessage()).isEqualTo("Invalid message cursor");
				});
	}

	private String encoded(String payload) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}
}
