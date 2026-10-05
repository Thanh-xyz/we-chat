package main.com.chat.wechat.realtime.config;

import io.micrometer.core.instrument.MeterRegistry;
import main.com.chat.wechat.realtime.service.LocalRealtimeDelivery;
import main.com.chat.wechat.realtime.service.RedisRealtimeBroker;
import main.com.chat.wechat.realtime.service.RedisRealtimeListener;
import main.com.chat.wechat.realtime.service.RealtimeBroker;
import main.com.chat.wechat.realtime.service.RealtimeDestinationPolicy;
import main.com.chat.wechat.realtime.service.RealtimeEnvelopeFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RealtimeProperties.class)
@ConditionalOnProperty(name = "app.realtime.distributed-enabled", havingValue = "true")
public class RedisRealtimeConfiguration {
	@Bean(name = "realtimeRedisPublisherExecutor")
	ThreadPoolTaskExecutor realtimeRedisPublisherExecutor(
			RealtimeProperties properties,
			MeterRegistry meterRegistry) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.publisherCoreSize());
		executor.setMaxPoolSize(properties.publisherMaxSize());
		executor.setQueueCapacity(properties.publisherQueueCapacity());
		executor.setThreadNamePrefix("realtime-redis-pub-");
		executor.setRejectedExecutionHandler(rejectionHandler(meterRegistry, "publisher"));
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(properties.shutdownTimeoutSeconds());
		executor.initialize();
		registerExecutorMetrics(meterRegistry, "publisher", executor);
		return executor;
	}

	@Bean(name = "realtimeRedisListenerExecutor")
	ThreadPoolTaskExecutor realtimeRedisListenerExecutor(
			RealtimeProperties properties,
			MeterRegistry meterRegistry) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.listenerCoreSize());
		executor.setMaxPoolSize(properties.listenerMaxSize());
		executor.setQueueCapacity(properties.listenerQueueCapacity());
		executor.setThreadNamePrefix("realtime-redis-sub-");
		executor.setRejectedExecutionHandler(rejectionHandler(meterRegistry, "listener"));
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(properties.shutdownTimeoutSeconds());
		executor.initialize();
		registerExecutorMetrics(meterRegistry, "listener", executor);
		return executor;
	}

	@Bean
	RealtimeBroker redisRealtimeBroker(
			StringRedisTemplate redisTemplate,
			RealtimeProperties properties,
			RealtimeEnvelopeFactory envelopeFactory,
			@Qualifier("realtimeRedisPublisherExecutor") TaskExecutor publisherExecutor,
			MeterRegistry meterRegistry) {
		return new RedisRealtimeBroker(redisTemplate, properties, envelopeFactory, publisherExecutor, meterRegistry);
	}

	@Bean
	RedisRealtimeListener redisRealtimeListener(
			RealtimeProperties properties,
			RealtimeEnvelopeFactory envelopeFactory,
			RealtimeDestinationPolicy destinationPolicy,
			LocalRealtimeDelivery localRealtimeDelivery,
			MeterRegistry meterRegistry) {
		return new RedisRealtimeListener(properties, envelopeFactory, destinationPolicy, localRealtimeDelivery, meterRegistry);
	}

	@Bean
	RedisMessageListenerContainer realtimeRedisMessageListenerContainer(
			RedisConnectionFactory connectionFactory,
			RedisRealtimeListener listener,
			RealtimeProperties properties,
			@Qualifier("realtimeRedisListenerExecutor") TaskExecutor listenerExecutor) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.setTaskExecutor(listenerExecutor);
		container.addMessageListener(listener, new ChannelTopic(properties.channel()));
		return container;
	}

	@Bean(name = "realtimeRedis")
	HealthIndicator realtimeRedisHealthIndicator(RedisConnectionFactory connectionFactory) {
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

	private void registerExecutorMetrics(
			MeterRegistry meterRegistry,
			String kind,
			ThreadPoolTaskExecutor executor) {
		meterRegistry.gauge("realtime_redis_executor_active", TagsHelper.tags(kind), executor,
				taskExecutor -> taskExecutor.getThreadPoolExecutor().getActiveCount());
		meterRegistry.gauge("realtime_redis_executor_queue_depth", TagsHelper.tags(kind), executor,
				taskExecutor -> taskExecutor.getThreadPoolExecutor().getQueue().size());
		meterRegistry.gauge("realtime_redis_executor_queue_remaining", TagsHelper.tags(kind), executor,
				taskExecutor -> taskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());
	}

	private java.util.concurrent.RejectedExecutionHandler rejectionHandler(
			MeterRegistry meterRegistry,
			String executorKind) {
		return (task, executor) -> {
			io.micrometer.core.instrument.Counter.builder("realtime_redis_executor_rejections_total")
					.tag("executor", executorKind)
					.register(meterRegistry)
					.increment();
			throw new RejectedExecutionException("Realtime Redis executor queue is full");
		};
	}

	private static final class TagsHelper {
		private static io.micrometer.core.instrument.Tags tags(String kind) {
			return io.micrometer.core.instrument.Tags.of("executor", kind);
		}
	}
}
