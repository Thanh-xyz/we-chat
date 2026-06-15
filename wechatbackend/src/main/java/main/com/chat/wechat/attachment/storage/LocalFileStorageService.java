package main.com.chat.wechat.attachment.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {
	private final Path root;

	public LocalFileStorageService(StorageProperties storageProperties) throws IOException {
		this.root = Path.of(storageProperties.localRoot()).toAbsolutePath().normalize();
		Files.createDirectories(root);
	}

	@Override
	public StoredFile upload(String storageKey, InputStream content, long contentLength, String contentType) throws IOException {
		Path target = resolve(storageKey);
		Files.createDirectories(target.getParent());
		Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
		return new StoredFile(storageKey, Files.size(target));
	}

	@Override
	public Resource download(String storageKey) throws IOException {
		Path path = resolve(storageKey);
		if (!Files.exists(path) || !Files.isRegularFile(path)) {
			throw new IOException("Stored file not found");
		}
		return new FileSystemResource(path);
	}

	@Override
	public void delete(String storageKey) throws IOException {
		Files.deleteIfExists(resolve(storageKey));
	}

	@Override
	public boolean exists(String storageKey) {
		try {
			Path path = resolve(storageKey);
			return Files.exists(path) && Files.isRegularFile(path);
		} catch (IOException exception) {
			return false;
		}
	}

	@Override
	public String publicUrl(String storageKey) {
		return null;
	}

	@Override
	public String signedUrl(String storageKey, Duration ttl) {
		return null;
	}

	private Path resolve(String storageKey) throws IOException {
		if (!StringUtils.hasText(storageKey) || storageKey.contains("\\") || storageKey.startsWith("/")) {
			throw new IOException("Invalid storage key");
		}
		Path path = root.resolve(storageKey).normalize();
		if (!path.startsWith(root)) {
			throw new IOException("Invalid storage key");
		}
		return path;
	}
}
