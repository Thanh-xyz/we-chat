package main.com.chat.wechat.audit.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class AuditSanitizer {
	public static final String MASK = "***MASKED***";
	private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
			"(\"(?i:password|newPassword|currentPassword|token|rawToken|refreshToken|accessToken|authorization|secret|clientSecret|emailVerificationToken|passwordResetToken|storageKey|fileUrl|privateUrl|privatePath)\"\\s*:\\s*)(\"(?:\\\\.|[^\"])*\"|null|true|false|-?\\d+(?:\\.\\d+)?|\\{[^}]*}|\\[[^]]*])");
	private static final Pattern AUTHORIZATION_VALUE = Pattern.compile("(?i)(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+");

	public String sanitize(String value) {
		if (value == null || value.isBlank()) {
			return value;
		}
		String masked = SENSITIVE_JSON_FIELD.matcher(value).replaceAll("$1\"" + MASK + "\"");
		return AUTHORIZATION_VALUE.matcher(masked).replaceAll("$1 " + MASK);
	}
}
