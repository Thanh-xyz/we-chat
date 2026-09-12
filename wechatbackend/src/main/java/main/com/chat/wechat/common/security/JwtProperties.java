package main.com.chat.wechat.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		String secret,
		String issuer,
		Duration accessTokenTtl,
		Duration refreshTokenTtl) {
	private static final String KNOWN_DEVELOPMENT_SECRET_SHA256 =
			"ab6384178b60ecdb48ebbd82c57efbd5f62790f38c751b554f74acceb9106ae2";

	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("app.jwt.secret must be configured");
		}
		if (isKnownDevelopmentSecret(secret)) {
			throw new IllegalArgumentException("app.jwt.secret must not use the known development secret");
		}
		if (secret.length() < 32) {
			throw new IllegalArgumentException("app.jwt.secret must be at least 32 characters");
		}
	}

	private static boolean isKnownDevelopmentSecret(String secret) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(secret.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest).equals(KNOWN_DEVELOPMENT_SECRET_SHA256);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
