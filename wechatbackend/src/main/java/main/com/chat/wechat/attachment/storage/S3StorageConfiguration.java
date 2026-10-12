package main.com.chat.wechat.attachment.storage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageConfiguration {
	@Bean
	public S3Client s3Client(StorageProperties properties) {
		return clientBuilder(properties).build();
	}

	@Bean
	public S3Presigner s3Presigner(StorageProperties properties) {
		return S3Presigner.builder()
				.region(Region.of(properties.region()))
				.endpointOverride(URI.create(properties.presignedEndpoint()))
				.credentialsProvider(credentials(properties))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(properties.pathStyle())
						.build())
				.build();
	}

	@Bean("objectStorage")
	public HealthIndicator objectStorageHealth(S3ObjectStorageService storage) {
		return () -> storage.isAvailable()
				? Health.up().build()
				: Health.down().build();
	}

	@Bean
	public ApplicationRunner s3BucketInitializer(@Qualifier("s3Client") S3Client client, StorageProperties properties) {
		return args -> {
			if (bucketExists(client, properties.bucket())) {
				return;
			}
			try {
				client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
			} catch (S3Exception creationFailure) {
				String code = creationFailure.awsErrorDetails() == null
						? ""
						: creationFailure.awsErrorDetails().errorCode();
				if (!"BucketAlreadyExists".equals(code) && !"BucketAlreadyOwnedByYou".equals(code)) {
					throw creationFailure;
				}
			}
		};
	}

	private S3ClientBuilder clientBuilder(StorageProperties properties) {
		if (!properties.pathStyle()) {
			throw new IllegalArgumentException("Supabase S3 storage requires path-style addressing");
		}
		return S3Client.builder()
				.region(Region.of(properties.region()))
				.endpointOverride(URI.create(properties.endpoint()))
				.credentialsProvider(credentials(properties))
				.forcePathStyle(true)
				.httpClient(UrlConnectionHttpClient.builder()
						.connectionTimeout(properties.operationTimeout())
						.socketTimeout(properties.operationTimeout())
						.build());
	}

	private StaticCredentialsProvider credentials(StorageProperties properties) {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
	}

	private boolean bucketExists(S3Client client, String bucket) {
		try {
			client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
			return true;
		} catch (S3Exception exception) {
			int status = exception.statusCode();
			String code = exception.awsErrorDetails() == null ? "" : exception.awsErrorDetails().errorCode();
			if (status == 404 || "NoSuchBucket".equals(code)) {
				return false;
			}
			throw exception;
		}
	}
}
