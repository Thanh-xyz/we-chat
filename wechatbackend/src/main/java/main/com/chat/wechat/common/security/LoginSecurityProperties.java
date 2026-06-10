package main.com.chat.wechat.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security")
public record LoginSecurityProperties(
		int maxFailedLoginAttempts,
		Duration lockDuration) {

	public LoginSecurityProperties {
		if (maxFailedLoginAttempts <= 0) {
			maxFailedLoginAttempts = 5;
		}
		if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
			lockDuration = Duration.ofMinutes(15);
		}
	}
}
