package main.com.chat.wechat.role.model;

import java.time.Instant;
import java.util.UUID;

public record Permission(
		UUID id,
		String code,
		String name,
		String description,
		Instant createdAt) {
}
