package main.com.chat.wechat.attachment.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
		String type,
		String localRoot) {
	public StorageProperties {
		type = type == null || type.isBlank() ? "local" : type.trim().toLowerCase();
		localRoot = localRoot == null || localRoot.isBlank() ? "uploads" : localRoot.trim();
	}
}
