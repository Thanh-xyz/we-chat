package main.com.chat.wechat.attachment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "app.attachment")
public record AttachmentProperties(
		DataSize imageMaxSize,
		DataSize fileMaxSize,
		DataSize voiceMaxSize,
		List<String> allowedImageTypes,
		List<String> allowedFileTypes,
		List<String> allowedVoiceTypes) {
	private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
	private static final Set<String> VOICE_EXTENSIONS = Set.of("mp3", "wav", "m4a", "webm");
	private static final Set<String> FILE_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip");

	public AttachmentProperties {
		imageMaxSize = imageMaxSize == null ? DataSize.ofMegabytes(10) : imageMaxSize;
		fileMaxSize = fileMaxSize == null ? DataSize.ofMegabytes(50) : fileMaxSize;
		voiceMaxSize = voiceMaxSize == null ? DataSize.ofMegabytes(25) : voiceMaxSize;
		allowedImageTypes = normalize(allowedImageTypes, List.of("image/jpeg", "image/png", "image/webp", "image/gif"));
		allowedFileTypes = normalize(allowedFileTypes, List.of(
				"application/pdf",
				"application/msword",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				"application/vnd.ms-excel",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				"application/vnd.ms-powerpoint",
				"application/vnd.openxmlformats-officedocument.presentationml.presentation",
				"text/plain",
				"application/zip",
				"application/x-zip-compressed"));
		allowedVoiceTypes = normalize(allowedVoiceTypes, List.of(
				"audio/mpeg",
				"audio/wav",
				"audio/x-wav",
				"audio/mp4",
				"audio/webm",
				"audio/x-m4a"));
	}

	public long maxSizeFor(String fileType) {
		return switch (fileType) {
			case "IMAGE" -> imageMaxSize.toBytes();
			case "VOICE" -> voiceMaxSize.toBytes();
			default -> fileMaxSize.toBytes();
		};
	}

	public List<String> allowedMimeTypesFor(String fileType) {
		return switch (fileType) {
			case "IMAGE" -> allowedImageTypes;
			case "VOICE" -> allowedVoiceTypes;
			default -> allowedFileTypes;
		};
	}

	public Set<String> allowedExtensionsFor(String fileType) {
		return switch (fileType) {
			case "IMAGE" -> IMAGE_EXTENSIONS;
			case "VOICE" -> VOICE_EXTENSIONS;
			default -> FILE_EXTENSIONS;
		};
	}

	private static List<String> normalize(List<String> values, List<String> defaults) {
		List<String> source = values == null || values.isEmpty() ? defaults : values;
		return source.stream()
				.map(value -> value.trim().toLowerCase(Locale.ROOT))
				.filter(value -> !value.isBlank())
				.distinct()
				.toList();
	}
}
