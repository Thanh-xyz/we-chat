package main.com.chat.wechat.common.ratelimit;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A deliberately small set of identities that may be used by a rate-limit
 * policy. Keeping the type constrained prevents callers from accidentally
 * using credentials, request bodies, or unbounded request identifiers.
 */
public record RateLimitKey(String value) {
	private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9:._%~-]{1,128}");

	public RateLimitKey {
		if (value == null
				|| (!value.startsWith("ip:") && !value.startsWith("user:"))
				|| !SAFE_VALUE.matcher(value).matches()) {
			throw new IllegalArgumentException("Rate-limit identity contains unsafe characters");
		}
		if (value.startsWith("user:") && !isUuid(value.substring("user:".length()))) {
			throw new IllegalArgumentException("User rate-limit identity must be a UUID");
		}
	}

	public static RateLimitKey ip(String address) {
		String normalized = address == null ? "unknown" : address.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("[") && normalized.endsWith("]")) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		if (normalized.isBlank() || !SAFE_VALUE.matcher(normalized).matches()) {
			normalized = "unknown";
		}
		return new RateLimitKey("ip:" + normalized);
	}

	public static RateLimitKey user(UUID userId) {
		if (userId == null) {
			throw new IllegalArgumentException("userId is required for a user rate-limit identity");
		}
		return new RateLimitKey("user:" + userId);
	}

	private static boolean isUuid(String value) {
		try {
			return UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
