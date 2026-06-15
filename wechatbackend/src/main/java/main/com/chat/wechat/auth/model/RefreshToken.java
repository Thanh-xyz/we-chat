package main.com.chat.wechat.auth.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
		UUID id,
		UUID userId,
		String tokenHash,
		Instant expiresAt,
		Instant revokedAt,
		Instant createdAt,
		String replacedByToken,
		String deviceInfo,
		String ipAddress) {

	public boolean activeAt(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}
}
