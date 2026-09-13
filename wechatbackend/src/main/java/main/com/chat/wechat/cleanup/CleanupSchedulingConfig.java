package main.com.chat.wechat.cleanup;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CleanupProperties.class)
public class CleanupSchedulingConfig {
	@Bean
	public Clock cleanupClock() {
		return Clock.systemUTC();
	}
}

