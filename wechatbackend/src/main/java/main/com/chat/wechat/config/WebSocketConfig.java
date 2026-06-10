package main.com.chat.wechat.config;

import main.com.chat.wechat.realtime.security.WebSocketAuthChannelInterceptor;
import main.com.chat.wechat.realtime.config.WebSocketProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;
	private final WebSocketProperties webSocketProperties;

	public WebSocketConfig(
			WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor,
			WebSocketProperties webSocketProperties) {
		this.webSocketAuthChannelInterceptor = webSocketAuthChannelInterceptor;
		this.webSocketProperties = webSocketProperties;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(webSocketAuthChannelInterceptor);
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				.setAllowedOriginPatterns(webSocketProperties.allowedOrigins().toArray(String[]::new));
	}
}
