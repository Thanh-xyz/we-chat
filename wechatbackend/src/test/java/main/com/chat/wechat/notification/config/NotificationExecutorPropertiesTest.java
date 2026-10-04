package main.com.chat.wechat.notification.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class NotificationExecutorPropertiesTest {
	@Test
	void acceptsAValidBoundedExecutorConfiguration() {
		NotificationExecutorProperties properties = new NotificationExecutorProperties(
				2, 8, 100, 50, Duration.ofSeconds(10));

		assertThat(properties.coreSize()).isEqualTo(2);
		assertThat(properties.maxSize()).isEqualTo(8);
		assertThat(properties.queueCapacity()).isEqualTo(100);
		assertThat(properties.batchSize()).isEqualTo(50);
	}

	@Test
	void rejectsUnsafeExecutorConfiguration() {
		assertThatIllegalArgumentException().isThrownBy(() -> properties(0, 8, 100, 50));
		assertThatIllegalArgumentException().isThrownBy(() -> properties(4, 2, 100, 50));
		assertThatIllegalArgumentException().isThrownBy(() -> properties(2, 8, 0, 50));
		assertThatIllegalArgumentException().isThrownBy(() -> properties(2, 8, 100, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new NotificationExecutorProperties(
				2, 8, 100, 50, Duration.ZERO));
	}

	private NotificationExecutorProperties properties(int coreSize, int maxSize, int queueCapacity, int batchSize) {
		return new NotificationExecutorProperties(
				coreSize, maxSize, queueCapacity, batchSize, Duration.ofSeconds(10));
	}
}
