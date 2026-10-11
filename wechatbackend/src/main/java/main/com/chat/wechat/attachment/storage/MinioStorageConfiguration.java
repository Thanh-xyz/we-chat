package main.com.chat.wechat.attachment.storage;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
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

	@Bean
	public ApplicationRunner minioBucketInitializer(
			@Qualifier("minioClient") MinioClient client,
			StorageProperties properties) {
		return args -> {
			if (client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
				return;
			}
			try {
				client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
			} catch (Exception creationFailure) {
				// Two replicas may observe an empty bucket at the same time. Treat
				// the already-owned race as successful, but fail for other errors.
				if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
					throw creationFailure;
				}
			}
		};
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
