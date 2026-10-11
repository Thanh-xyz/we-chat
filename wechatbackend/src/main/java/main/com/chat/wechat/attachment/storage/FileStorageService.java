package main.com.chat.wechat.attachment.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public interface FileStorageService {
	StoredFile upload(String storageKey, InputStream content, long contentLength, String contentType) throws IOException;

	PresignedObjectRequest presignedUpload(String storageKey, String contentType, long contentLength, Duration ttl) throws IOException;

	Resource download(String storageKey) throws IOException;

	InputStream openStream(String storageKey) throws IOException;

	StoredObject head(String storageKey) throws IOException;

	void delete(String storageKey) throws IOException;

	boolean exists(String storageKey);

	boolean isAvailable();

	String publicUrl(String storageKey);

	String signedUrl(String storageKey, Duration ttl) throws IOException;

	record PresignedObjectRequest(String url, java.util.Map<String, String> headers, java.time.Instant expiresAt) {
	}

	record StoredObject(String storageKey, long size, String contentType) {
	}
}
