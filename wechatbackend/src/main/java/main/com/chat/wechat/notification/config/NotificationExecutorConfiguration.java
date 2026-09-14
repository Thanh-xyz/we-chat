package main.com.chat.wechat.notification.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationExecutorProperties.class)
public class NotificationExecutorConfiguration {
	@Bean(name = "notificationTaskExecutor")
	ThreadPoolTaskExecutor notificationTaskExecutor(
			NotificationExecutorProperties properties,
			MeterRegistry meterRegistry) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.coreSize());
		executor.setMaxPoolSize(properties.maxSize());
		executor.setQueueCapacity(properties.queueCapacity());
		executor.setKeepAliveSeconds(60);
		executor.setThreadNamePrefix("notification-dispatch-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(Math.toIntExact(properties.shutdownTimeout().toSeconds()));
		executor.initialize();

		ThreadPoolExecutor threadPool = executor.getThreadPoolExecutor();
		Gauge.builder("notification_executor_active_tasks", threadPool, ThreadPoolExecutor::getActiveCount)
				.description("Active in-process notification dispatch tasks")
				.register(meterRegistry);
		Gauge.builder("notification_executor_queue_size", threadPool, pool -> pool.getQueue().size())
				.description("Queued in-process notification dispatch tasks")
				.register(meterRegistry);
		Gauge.builder("notification_executor_completed_tasks", threadPool, ThreadPoolExecutor::getCompletedTaskCount)
				.description("Completed in-process notification dispatch tasks")
				.register(meterRegistry);
		return executor;
	}

}
