package main.com.chat.wechat.message.pagination;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MessageCursor(Instant createdAt, UUID id) {
	public MessageCursor {
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(id, "id");
	}
}

