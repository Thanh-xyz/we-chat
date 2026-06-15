package main.com.chat.wechat.attachment.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public interface FileStorageService {
	StoredFile upload(String storageKey, InputStream content, long contentLength, String contentType) throws IOException;

	Resource download(String storageKey) throws IOException;

	void delete(String storageKey) throws IOException;

	boolean exists(String storageKey);

	String publicUrl(String storageKey);

	String signedUrl(String storageKey, Duration ttl);
}
