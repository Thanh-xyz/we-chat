package main.com.chat.wechat.attachment.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.Http.Method;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioObjectStorageService implements FileStorageService {
	private final MinioClient client;
	private final MinioClient presignedClient;
	private final StorageProperties properties;

	public MinioObjectStorageService(
			@Qualifier("minioClient")
			MinioClient client,
			@Qualifier("minioPresignedClient")
			MinioClient presignedClient,
			StorageProperties properties) {
		this.client = client;
		this.presignedClient = presignedClient;
		this.properties = properties;
	}

	@Override
	public StoredFile upload(String storageKey, InputStream content, long contentLength, String contentType) throws IOException {
		try {
			client.putObject(PutObjectArgs.builder()
					.bucket(properties.bucket())
					.object(validateKey(storageKey))
					.stream(content, contentLength, -1L)
					.contentType(normalizeContentType(contentType))
					.build());
			return new StoredFile(storageKey, contentLength);
		} catch (Exception exception) {
			throw storageIOException("upload", exception);
		}
	}

	@Override
	public PresignedObjectRequest presignedUpload(String storageKey, String contentType, long contentLength, Duration ttl) throws IOException {
		try {
			long expirySeconds = boundedExpirySeconds(ttl);
			String url = presignedClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
					.method(Method.PUT)
					.bucket(properties.bucket())
					.object(validateKey(storageKey))
					.expiry((int) expirySeconds, TimeUnit.SECONDS)
					.build());
			return new PresignedObjectRequest(
					url,
					Map.of("Content-Type", normalizeContentType(contentType)),
					Instant.now().plusSeconds(expirySeconds));
		} catch (Exception exception) {
			throw storageIOException("presign_upload", exception);
		}
	}

	@Override
	public Resource download(String storageKey) throws IOException {
		return new InputStreamResource(openStream(storageKey));
	}

	@Override
	public InputStream openStream(String storageKey) throws IOException {
		try {
			return client.getObject(GetObjectArgs.builder()
					.bucket(properties.bucket())
					.object(validateKey(storageKey))
					.build());
		} catch (Exception exception) {
			throw storageIOException("download", exception);
		}
	}

	@Override
	public StoredObject head(String storageKey) throws IOException {
		try {
			StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
					.bucket(properties.bucket())
					.object(validateKey(storageKey))
					.build());
			return new StoredObject(storageKey, stat.size(), stat.contentType());
		} catch (Exception exception) {
			throw storageIOException("head", exception);
		}
	}

	@Override
	public void delete(String storageKey) throws IOException {
		try {
			client.removeObject(RemoveObjectArgs.builder()
					.bucket(properties.bucket())
					.object(validateKey(storageKey))
					.build());
		} catch (Exception exception) {
			throw storageIOException("delete", exception);
		}
	}

	@Override
	public boolean exists(String storageKey) {
		try {
			head(storageKey);
			return true;
		} catch (IOException exception) {
			return false;
		}
	}

	@Override
	public boolean isAvailable() {
		return healthCheck();
	}

	@Override
	public String publicUrl(String storageKey) {
		return null;
	}

	@Override
	public String signedUrl(String storageKey, Duration ttl) throws IOException {
		try {
			long expirySeconds = boundedExpirySeconds(ttl);
			return presignedClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
					.method(Method.GET)
					.bucket(properties.bucket())
					.object(validateKey(storageKey))
					.expiry((int) expirySeconds, TimeUnit.SECONDS)
					.build());
		} catch (Exception exception) {
			throw storageIOException("presign_download", exception);
		}
	}

	public boolean healthCheck() {
		try {
			client.bucketExists(io.minio.BucketExistsArgs.builder().bucket(properties.bucket()).build());
			return true;
		} catch (Exception exception) {
			return false;
		}
	}

	private String validateKey(String storageKey) throws IOException {
		if (!StringUtils.hasText(storageKey)
				|| storageKey.length() > 512
				|| storageKey.startsWith("/")
				|| storageKey.contains("\\")
				|| storageKey.contains("..")
				|| storageKey.chars().anyMatch(Character::isISOControl)) {
			throw new IOException("Invalid storage key");
		}
		return storageKey;
	}

	private String normalizeContentType(String contentType) {
		return StringUtils.hasText(contentType) ? contentType.trim().toLowerCase() : "application/octet-stream";
	}

	private long boundedExpirySeconds(Duration ttl) throws IOException {
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw new IOException("Invalid presigned URL TTL");
		}
		return Math.max(1, Math.min(TimeUnit.HOURS.toSeconds(1), ttl.toSeconds()));
	}

	private IOException storageIOException(String operation, Exception exception) {
		return new IOException("Object storage operation failed: " + operation, exception);
	}
}
