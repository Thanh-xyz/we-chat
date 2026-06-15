package main.com.chat.wechat.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security")
public record LoginSecurityProperties(
		int maxFailedLoginAttempts,
		Duration lockDuration,
		boolean requireEmailVerified,
		Duration emailVerificationTokenTtl,
		Duration passwordResetTokenTtl,
		Duration verificationEmailCooldown) {
	private static final Duration MIN_TOKEN_TTL = Duration.ofMinutes(1);

	public LoginSecurityProperties {
		if (maxFailedLoginAttempts <= 0) {
			maxFailedLoginAttempts = 3;
		}
		if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
			lockDuration = Duration.ofMinutes(15);
		}
		if (emailVerificationTokenTtl == null || emailVerificationTokenTtl.isNegative() || emailVerificationTokenTtl.isZero()) {
			emailVerificationTokenTtl = Duration.ofHours(24);
		}
		if (emailVerificationTokenTtl.compareTo(MIN_TOKEN_TTL) < 0) {
			throw new IllegalArgumentException("app.security.email-verification-token-ttl must be at least PT1M. Use ISO-8601 values such as PT24H.");
		}
		if (passwordResetTokenTtl == null || passwordResetTokenTtl.isNegative() || passwordResetTokenTtl.isZero()) {
			passwordResetTokenTtl = Duration.ofMinutes(15);
		}
		if (passwordResetTokenTtl.compareTo(MIN_TOKEN_TTL) < 0) {
			throw new IllegalArgumentException("app.security.password-reset-token-ttl must be at least PT1M. Use ISO-8601 values such as PT15M.");
		}
		if (verificationEmailCooldown == null || verificationEmailCooldown.isNegative()) {
			verificationEmailCooldown = Duration.ofMinutes(5);
		}
	}
}
