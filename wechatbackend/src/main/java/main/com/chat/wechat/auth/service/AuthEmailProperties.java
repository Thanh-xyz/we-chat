package main.com.chat.wechat.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthEmailProperties(
		String emailVerificationUrl,
		String passwordResetUrl) {

	public AuthEmailProperties {
		if (emailVerificationUrl == null || emailVerificationUrl.isBlank()) {
			throw new IllegalArgumentException("app.auth.email-verification-url must be configured");
		}
		if (passwordResetUrl == null || passwordResetUrl.isBlank()) {
			throw new IllegalArgumentException("app.auth.password-reset-url must be configured");
		}
	}
}
