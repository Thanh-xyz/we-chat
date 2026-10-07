package main.com.chat.wechat.common.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.rate-limit.distributed-enabled", havingValue = "false", matchIfMissing = true)
public class LocalRateLimitConfiguration {
	@Bean
	RateLimiter inMemoryRateLimiter(RateLimitProperties properties, MeterRegistry meterRegistry) {
		return new InMemoryRateLimiter(properties.fallbackMaxEntries(), meterRegistry);
	}
}
