package main.com.chat.wechat.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.rate-limit.distributed-enabled", havingValue = "true")
public class RedisRateLimitConfiguration {
	@Bean
	RateLimiter redisRateLimiter(
			StringRedisTemplate redisTemplate,
			RateLimitProperties properties,
			MeterRegistry meterRegistry) {
		return new RedisRateLimiter(redisTemplate, properties, meterRegistry);
	}

	@Bean(name = "rateLimitRedis")
	HealthIndicator rateLimitRedisHealthIndicator(RedisConnectionFactory connectionFactory) {
		return () -> {
			RedisConnection connection = null;
			try {
				connection = connectionFactory.getConnection();
				String pong = connection.ping();
				return "PONG".equalsIgnoreCase(pong) ? Health.up().build() : Health.down().build();
			} catch (Exception exception) {
				return Health.down().build();
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		};
	}
}
