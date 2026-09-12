package main.com.chat.wechat.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.mail")
public final class AuthMailProperties {
	private final String host;
	private final int port;
	private final String username;
	private final String password;
	private final String from;

	public AuthMailProperties(String host, int port, String username, String password, String from) {
		this.host = required(host, "app.auth.mail.host");
		if (port < 1 || port > 65_535) {
			throw new IllegalArgumentException("app.auth.mail.port must be between 1 and 65535");
		}
		this.port = port;
		this.username = required(username, "app.auth.mail.username");
		this.password = requiredSecret(password, "app.auth.mail.password");
		this.from = required(from, "app.auth.mail.from");
	}

	public String host() {
		return host;
	}

	public int port() {
		return port;
	}

	public String username() {
		return username;
	}

	public String password() {
		return password;
	}

	public String from() {
		return from;
	}

	private static String required(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(propertyName + " must be configured");
		}
		return value.trim();
	}

	private static String requiredSecret(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(propertyName + " must be configured");
		}
		return value;
	}
}
