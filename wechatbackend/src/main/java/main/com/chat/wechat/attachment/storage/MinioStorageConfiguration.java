package main.com.chat.wechat.attachment.storage;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioStorageConfiguration {
	@Bean
	public MinioClient minioClient(StorageProperties properties) {
		return builder(properties.endpoint(), properties).build();
	}

	@Bean
	public MinioClient minioPresignedClient(StorageProperties properties) {
		return builder(properties.presignedEndpoint(), properties).build();
	}

	@Bean("objectStorage")
	public HealthIndicator objectStorageHealth(MinioObjectStorageService storage) {
		return () -> storage.healthCheck()
				? Health.up().build()
				: Health.down().build();
	}

	private MinioClient.Builder builder(String endpoint, StorageProperties properties) {
		MinioClient.Builder builder = MinioClient.builder()
				.endpoint(endpoint)
				.credentials(properties.accessKey(), properties.secretKey());
		if (properties.region() != null && !properties.region().isBlank()) {
			builder.region(properties.region());
		}
		return builder;
	}
}
