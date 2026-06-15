package main.com.chat.wechat.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthEmailProperties(
		String emailVerificationUrl,
		String passwordResetUrl) {

	public AuthEmailProperties {
		if (emailVerificationUrl == null || emailVerificationUrl.isBlank()) {
			emailVerificationUrl = "http://localhost:5173/verify-email?token=";
		}
		if (passwordResetUrl == null || passwordResetUrl.isBlank()) {
			passwordResetUrl = "http://localhost:5173/reset-password?token=";
		}
	}
}
