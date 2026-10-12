package main.com.chat.wechat.attachment.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ObjectStorageService implements FileStorageService {
	private final S3Client client;
	private final S3Presigner presigner;
	private final StorageProperties properties;

	public S3ObjectStorageService(S3Client client, S3Presigner presigner, StorageProperties properties) {
		this.client = client;
		this.presigner = presigner;
		this.properties = properties;
	}

	@Override
	public StoredFile upload(String storageKey, InputStream content, long contentLength, String contentType) throws IOException {
		try {
			PutObjectRequest request = PutObjectRequest.builder()
					.bucket(properties.bucket())
					.key(validateKey(storageKey))
					.contentType(normalizeContentType(contentType))
					.build();
			client.putObject(request, RequestBody.fromInputStream(content, contentLength));
			return new StoredFile(storageKey, contentLength);
		} catch (Exception exception) {
			throw storageIOException("upload", exception);
		}
	}

	@Override
	public PresignedObjectRequest presignedUpload(String storageKey, String contentType, long contentLength, Duration ttl) throws IOException {
		try {
			long expirySeconds = boundedExpirySeconds(ttl);
			PutObjectRequest putObject = PutObjectRequest.builder()
					.bucket(properties.bucket())
					.key(validateKey(storageKey))
					.contentType(normalizeContentType(contentType))
					.build();
			PutObjectPresignRequest presign = PutObjectPresignRequest.builder()
					.signatureDuration(Duration.ofSeconds(expirySeconds))
					.putObjectRequest(putObject)
					.build();
			PresignedPutObjectRequest request = presigner.presignPutObject(presign);
			return new PresignedObjectRequest(
					request.url().toString(),
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
			return client.getObject(GetObjectRequest.builder()
					.bucket(properties.bucket())
					.key(validateKey(storageKey))
					.build());
		} catch (Exception exception) {
			throw storageIOException("download", exception);
		}
	}

	@Override
	public StoredObject head(String storageKey) throws IOException {
		try {
			HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
					.bucket(properties.bucket())
					.key(validateKey(storageKey))
					.build());
			return new StoredObject(storageKey, response.contentLength(), response.contentType());
		} catch (Exception exception) {
			throw storageIOException("head", exception);
		}
	}

	@Override
	public void delete(String storageKey) throws IOException {
		try {
			client.deleteObject(DeleteObjectRequest.builder()
					.bucket(properties.bucket())
					.key(validateKey(storageKey))
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
		try {
			client.headBucket(request -> request.bucket(properties.bucket()));
			return true;
		} catch (Exception exception) {
			return false;
		}
	}

	@Override
	public String publicUrl(String storageKey) {
		return null;
	}

	@Override
	public String signedUrl(String storageKey, Duration ttl) throws IOException {
		try {
			long expirySeconds = boundedExpirySeconds(ttl);
			GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
					.signatureDuration(Duration.ofSeconds(expirySeconds))
					.getObjectRequest(GetObjectRequest.builder()
							.bucket(properties.bucket())
							.key(validateKey(storageKey))
							.build())
					.build();
			PresignedGetObjectRequest request = presigner.presignGetObject(presign);
			return request.url().toString();
		} catch (Exception exception) {
			throw storageIOException("presign_download", exception);
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
