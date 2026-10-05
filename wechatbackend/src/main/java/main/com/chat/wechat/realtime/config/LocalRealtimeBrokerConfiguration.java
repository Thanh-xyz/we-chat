package main.com.chat.wechat.realtime.config;

import main.com.chat.wechat.realtime.service.LocalRealtimeDelivery;
import main.com.chat.wechat.realtime.service.RealtimeBroker;
import main.com.chat.wechat.realtime.service.RealtimeEnvelopeFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RealtimeProperties.class)
@ConditionalOnProperty(
		name = "app.realtime.distributed-enabled",
		havingValue = "false",
		matchIfMissing = true)
public class LocalRealtimeBrokerConfiguration {
	@Bean
	RealtimeBroker localRealtimeBroker(
			RealtimeEnvelopeFactory envelopeFactory,
			LocalRealtimeDelivery localRealtimeDelivery) {
		return (destination, eventType, payload) -> {
			try {
				localRealtimeDelivery.deliver(envelopeFactory.create(destination, eventType, payload));
			} catch (RuntimeException exception) {
				throw exception;
			}
		};
	}
}
