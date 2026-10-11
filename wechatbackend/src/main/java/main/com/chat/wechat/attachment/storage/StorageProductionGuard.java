package main.com.chat.wechat.attachment.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class StorageProductionGuard {
	public StorageProductionGuard(StorageProperties properties) {
		if (!"minio".equals(properties.type())) {
			throw new IllegalStateException("Production requires S3-compatible object storage; local filesystem storage is disabled");
		}
		if (properties.accessKey().isBlank() || properties.secretKey().isBlank()) {
			throw new IllegalStateException("Production object storage credentials are required");
		}
	}
}
