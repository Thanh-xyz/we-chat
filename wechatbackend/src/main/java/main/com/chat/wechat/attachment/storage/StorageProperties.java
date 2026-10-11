package main.com.chat.wechat.attachment.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
		String type,
		String localRoot,
		String endpoint,
		String presignedEndpoint,
		String accessKey,
		String secretKey,
		String bucket,
		String region,
		boolean pathStyle,
		Duration uploadUrlTtl,
		Duration downloadUrlTtl) {
	@ConstructorBinding
	public StorageProperties {
		type = type == null || type.isBlank() ? "local" : type.trim().toLowerCase();
		localRoot = localRoot == null || localRoot.isBlank() ? "uploads" : localRoot.trim();
		endpoint = endpoint == null || endpoint.isBlank() ? "http://localhost:9000" : endpoint.trim();
		presignedEndpoint = presignedEndpoint == null || presignedEndpoint.isBlank() ? endpoint : presignedEndpoint.trim();
		accessKey = accessKey == null ? "" : accessKey.trim();
		secretKey = secretKey == null ? "" : secretKey.trim();
		bucket = bucket == null || bucket.isBlank() ? "attachments" : bucket.trim();
		region = region == null || region.isBlank() ? "us-east-1" : region.trim();
		uploadUrlTtl = uploadUrlTtl == null ? Duration.ofMinutes(10) : uploadUrlTtl;
		downloadUrlTtl = downloadUrlTtl == null ? Duration.ofMinutes(5) : downloadUrlTtl;
		if (uploadUrlTtl.isNegative() || uploadUrlTtl.isZero() || uploadUrlTtl.compareTo(Duration.ofHours(1)) > 0) {
			throw new IllegalArgumentException("app.storage.upload-url-ttl must be between PT1S and PT1H");
		}
		if (downloadUrlTtl.isNegative() || downloadUrlTtl.isZero() || downloadUrlTtl.compareTo(Duration.ofHours(1)) > 0) {
			throw new IllegalArgumentException("app.storage.download-url-ttl must be between PT1S and PT1H");
		}
	}

	public StorageProperties(String type, String localRoot) {
		this(type, localRoot, "http://localhost:9000", "http://localhost:9000", "", "", "attachments", "us-east-1", true, Duration.ofMinutes(10), Duration.ofMinutes(5));
	}
}
