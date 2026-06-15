package main.com.chat.wechat.auth.model;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetToken(
		UUID id,
		UUID userId,
		String tokenHash,
		Instant expiresAt,
		Instant usedAt,
		Instant createdAt) {

	public boolean usableAt(Instant now) {
		return usedAt == null && expiresAt.isAfter(now);
	}
}
