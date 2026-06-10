package main.com.chat.wechat.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.websocket")
public record WebSocketProperties(List<String> allowedOrigins) {
	public WebSocketProperties {
		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			allowedOrigins = List.of("http://localhost:5173", "http://localhost:3000");
		}
	}
}
