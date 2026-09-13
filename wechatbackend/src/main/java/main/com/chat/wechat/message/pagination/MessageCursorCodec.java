package main.com.chat.wechat.message.pagination;

import main.com.chat.wechat.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class MessageCursorCodec {
	private static final String VERSION = "v1";
	private static final int MAX_ENCODED_LENGTH = 256;
	private static final Pattern BASE64_URL_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,256}");
	private static final String INVALID_CURSOR_MESSAGE = "Invalid message cursor";

	public String encode(MessageCursor cursor) {
		String payload = VERSION + "|" + cursor.createdAt() + "|" + cursor.id();
		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	public MessageCursor decode(String encodedCursor) {
		if (encodedCursor == null) {
			return null;
		}
		if (encodedCursor.isBlank()
				|| encodedCursor.length() > MAX_ENCODED_LENGTH
				|| !BASE64_URL_PATTERN.matcher(encodedCursor).matches()) {
			throw invalidCursor();
		}

		String payload;
		try {
			payload = new String(Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException exception) {
			throw invalidCursor();
		}

		String[] parts = payload.split("\\|", -1);
		if (parts.length != 3 || !VERSION.equals(parts[0])) {
			throw invalidCursor();
		}

		try {
			Instant createdAt = Instant.parse(parts[1]);
			UUID id = UUID.fromString(parts[2]);
			if (!id.toString().equalsIgnoreCase(parts[2])) {
				throw invalidCursor();
			}
			return new MessageCursor(createdAt, id);
		} catch (RuntimeException exception) {
			if (exception instanceof ApiException apiException) {
				throw apiException;
			}
			throw invalidCursor();
		}
	}

	private ApiException invalidCursor() {
		return new ApiException(HttpStatus.BAD_REQUEST, INVALID_CURSOR_MESSAGE);
	}
}

