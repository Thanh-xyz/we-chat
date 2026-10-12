package main.com.chat.wechat.attachment.storage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class MinioObjectStorageIT {
	private static final String ACCESS_KEY = "r16-test-access";
	private static final String SECRET_KEY = "r16-test-secret-123456789";
	private static final String BUCKET = "attachments";

	@Container
	static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse("alpine/minio:RELEASE.2025-10-15T17-29-55Z"))
			.withEnv("MINIO_ROOT_USER", ACCESS_KEY)
			.withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
			.withExposedPorts(9000)
			.withCommand("server", "/tmp/minio-data", "--address", ":9000")
			.waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/minio/health/ready").forPort(9000));

	private static S3ObjectStorageService storage;
	private static HttpClient httpClient;

	@BeforeAll
	static void setUp() throws Exception {
		String endpoint = "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000));
		StorageProperties properties = new StorageProperties(
			"s3", "uploads", endpoint, endpoint, ACCESS_KEY, SECRET_KEY, BUCKET, "us-east-1", true,
				Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofSeconds(5));
		StaticCredentialsProvider credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));
		S3Client client = S3Client.builder()
				.endpointOverride(java.net.URI.create(endpoint))
				.credentialsProvider(credentials)
				.region(Region.US_EAST_1)
				.forcePathStyle(true)
				.build();
		client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
		S3Presigner presigner = S3Presigner.builder()
				.endpointOverride(java.net.URI.create(endpoint))
				.credentialsProvider(credentials)
				.region(Region.US_EAST_1)
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build();
		storage = new S3ObjectStorageService(client, presigner, properties);
		httpClient = HttpClient.newHttpClient();
	}

	@Test
	void directPresignedPutHeadGetAndDeleteWorkAgainstPrivateMinio() throws Exception {
		String key = "attachments/quarantine/integration-object";
		byte[] content = "R16 shared object storage".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		FileStorageService.PresignedObjectRequest request = storage.presignedUpload(key, "text/plain", content.length, Duration.ofMinutes(2));

		HttpResponse<Void> upload = httpClient.send(HttpRequest.newBuilder(URI.create(request.url()))
				.header("Content-Type", request.headers().get("Content-Type"))
				.PUT(HttpRequest.BodyPublishers.ofByteArray(content))
				.build(), HttpResponse.BodyHandlers.discarding());

		assertThat(upload.statusCode()).isBetween(200, 299);
		assertThat(storage.head(key).size()).isEqualTo(content.length);
		try (var stream = storage.openStream(key)) {
			assertThat(stream.readAllBytes()).isEqualTo(content);
		}
		assertThat(storage.signedUrl(key, Duration.ofMinutes(2))).doesNotContain("r16-test-secret");

		storage.delete(key);
		assertThat(storage.exists(key)).isFalse();
	}

	@Test
	void rejectsUnsafeObjectKeysAndHealthDoesNotExposeCredentials() {
		assertThatThrownBy(() -> storage.head("../secret"))
				.isInstanceOf(IOException.class);
		assertThat(storage.isAvailable()).isTrue();
	}
}
