package main.com.chat.wechat.attachment.service;

import main.com.chat.wechat.attachment.dto.AttachmentDownloadResponse;
import main.com.chat.wechat.attachment.dto.AttachmentDownloadUrlResponse;
import main.com.chat.wechat.attachment.dto.AttachmentResponse;
import main.com.chat.wechat.attachment.dto.AttachmentUploadResponse;
import main.com.chat.wechat.attachment.dto.PresignedUploadRequest;
import main.com.chat.wechat.attachment.dto.PresignedUploadResponse;
import main.com.chat.wechat.attachment.storage.FileStorageService;
import main.com.chat.wechat.attachment.storage.FileStorageService.PresignedObjectRequest;
import main.com.chat.wechat.attachment.storage.FileStorageService.StoredObject;
import main.com.chat.wechat.attachment.storage.StorageProperties;
import main.com.chat.wechat.attachment.storage.StoredFile;
import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.service.ConversationService;
import main.com.chat.wechat.message.model.MessageAttachment;
import main.com.chat.wechat.message.repository.MessageAttachmentRepository;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AttachmentService {
	private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentService.class);
	private static final DateTimeFormatter STORAGE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);
	private static final Set<String> CLIENT_FILE_TYPES = Set.of("IMAGE", "FILE", "VOICE");

	private final ConversationService conversationService;
	private final MessageAttachmentRepository messageAttachmentRepository;
	private final UserRepository userRepository;
	private final FileStorageService fileStorageService;
	private final AttachmentProperties attachmentProperties;
	private final AuditLogService auditLogService;
	private final AuditJsonWriter auditJsonWriter;
	private final RealtimeEventPublisher realtimeEventPublisher;
	private final MeterRegistry meterRegistry;
	private final StorageProperties storageProperties;

	public AttachmentService(
			ConversationService conversationService,
			MessageAttachmentRepository messageAttachmentRepository,
			UserRepository userRepository,
			FileStorageService fileStorageService,
			AttachmentProperties attachmentProperties,
			AuditLogService auditLogService,
			AuditJsonWriter auditJsonWriter,
			RealtimeEventPublisher realtimeEventPublisher,
			MeterRegistry meterRegistry,
			StorageProperties storageProperties) {
		this.conversationService = conversationService;
		this.messageAttachmentRepository = messageAttachmentRepository;
		this.userRepository = userRepository;
		this.fileStorageService = fileStorageService;
		this.attachmentProperties = attachmentProperties;
		this.auditLogService = auditLogService;
		this.auditJsonWriter = auditJsonWriter;
		this.realtimeEventPublisher = realtimeEventPublisher;
		this.meterRegistry = meterRegistry;
		this.storageProperties = storageProperties;
	}

	@Transactional
	public AttachmentUploadResponse upload(UUID actorUserId, UUID conversationId, String requestedFileType, MultipartFile file) {
		findActiveUser(actorUserId);
		Conversation conversation = conversationService.findAccessibleConversation(actorUserId, conversationId);
		ValidatedFile validatedFile = validateFile(file, requestedFileType);
		UUID attachmentId = UUID.randomUUID();
		Instant now = Instant.now();
		String storageKey = storageKey(conversation.id(), attachmentId, validatedFile.extension(), now);
		StoredFile storedFile;
		try (InputStream content = file.getInputStream()) {
			storedFile = fileStorageService.upload(storageKey, content, file.getSize(), validatedFile.mimeType());
		} catch (IOException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store attachment");
		}

		MessageAttachment attachment = new MessageAttachment(
				attachmentId,
				null,
				actorUserId,
				conversation.id(),
				validatedFile.originalFileName(),
				storedFile.storageKey(),
				"/api/attachments/" + attachmentId + "/download",
				validatedFile.mimeType(),
				validatedFile.fileType(),
				storedFile.fileSize(),
				validatedFile.checksum(),
				"CLEAN",
				null,
				now,
				now,
				"READY");
		try {
			messageAttachmentRepository.save(attachment);
			if (validatedFile.dimensions() != null) {
				messageAttachmentRepository.updateMediaMetadata(
						attachment.id(),
						validatedFile.dimensions().width(),
						validatedFile.dimensions().height(),
						null,
						now);
			}
		} catch (RuntimeException exception) {
			deleteStoredFileQuietly(storageKey);
			throw exception;
		}
		auditLogService.log(
				"ATTACHMENT_UPLOAD",
				"ATTACHMENT",
				attachment.id().toString(),
				null,
				auditJsonWriter.write(new AttachmentAuditValue(attachment.conversationId(), attachment.originalFileName(), attachment.fileType(), attachment.fileSize())));
		auditLogService.logSuccess(
				"MEDIA_UPLOAD",
				"ATTACHMENT",
				attachment.id().toString(),
				null,
				null,
				auditJsonWriter.write(new AttachmentAuditValue(attachment.conversationId(), attachment.originalFileName(), attachment.fileType(), attachment.fileSize())));
		realtimeEventPublisher.publishToMembersAfterCommit(
				conversationService.memberIds(attachment.conversationId()),
				RealtimeEvent.of(
						"media.created",
						attachment.conversationId(),
						attachment.messageId(),
						actorUserId,
						null,
						Map.of(
								"conversationId", attachment.conversationId(),
								"attachmentId", attachment.id(),
								"category", attachment.fileType())));
		return AttachmentUploadResponse.from(attachment);
	}

	public AttachmentResponse get(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		return AttachmentResponse.from(findAccessibleAttachment(actorUserId, attachmentId));
	}

	public AttachmentDownloadResponse download(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		MessageAttachment attachment = findAccessibleAttachment(actorUserId, attachmentId);
		if (!fileStorageService.isAvailable()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment storage is temporarily unavailable");
		}
		if (!fileStorageService.exists(attachment.storageKey())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Attachment file not found");
		}
		try {
			messageAttachmentRepository.incrementDownloadCount(attachment.id());
			auditLogService.log(
					"ATTACHMENT_DOWNLOAD",
					"ATTACHMENT",
					attachment.id().toString(),
					null,
					auditJsonWriter.write(new AttachmentAuditValue(
							attachment.conversationId(),
							attachment.originalFileName(),
							attachment.fileType(),
							attachment.fileSize())));
			return new AttachmentDownloadResponse(
					fileStorageService.download(attachment.storageKey()),
					attachment.originalFileName(),
					attachment.mimeType(),
					attachment.fileSize());
		} catch (IOException exception) {
			storageFailure("download", exception);
			throw new ApiException(HttpStatus.NOT_FOUND, "Attachment file not found");
		}
	}

	public AttachmentDownloadUrlResponse createDownloadUrl(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		MessageAttachment attachment = findAccessibleAttachment(actorUserId, attachmentId);
		if (!fileStorageService.isAvailable()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment storage is temporarily unavailable");
		}
		if (!fileStorageService.exists(attachment.storageKey())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Attachment file not found");
		}
		try {
			String url = fileStorageService.signedUrl(attachment.storageKey(), storageProperties.downloadUrlTtl());
			counter("storage_download_url", "success", attachment.fileType()).increment();
			return new AttachmentDownloadUrlResponse(url, Instant.now().plus(storageProperties.downloadUrlTtl()));
		} catch (IOException exception) {
			storageFailure("presign_download", exception);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment download is temporarily unavailable");
		}
	}

	public void delete(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		MessageAttachment attachment = findAccessibleAttachment(actorUserId, attachmentId);
		if (!actorUserId.equals(attachment.uploaderId())) {
			auditLogService.logFailure(
					"SECURITY_ACCESS_DENIED",
					"ATTACHMENT",
					attachment.id().toString(),
					"Only the uploader can delete this attachment",
					auditJsonWriter.write(new AccessDeniedAuditValue(actorUserId, "DELETE")),
					null);
			throw new ApiException(HttpStatus.FORBIDDEN, "Only the uploader can delete this attachment");
		}
		Instant deletedAt = Instant.now();
		messageAttachmentRepository.softDelete(attachment.id(), deletedAt);
		try {
			fileStorageService.delete(attachment.storageKey());
			messageAttachmentRepository.markStorageDeleted(attachment.id(), Instant.now());
			counter("storage_delete", "success", attachment.fileType()).increment();
		} catch (IOException exception) {
			storageFailure("delete", exception);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment deletion is temporarily unavailable");
		}
		auditLogService.log(
				"ATTACHMENT_DELETE",
				"ATTACHMENT",
				attachment.id().toString(),
				auditJsonWriter.write(new AttachmentAuditValue(attachment.conversationId(), attachment.originalFileName(), attachment.fileType(), attachment.fileSize())),
				null);
		realtimeEventPublisher.publishToMembersAfterCommit(
				conversationService.memberIds(attachment.conversationId()),
				RealtimeEvent.of(
						"attachment.deleted",
						attachment.conversationId(),
						attachment.messageId(),
						actorUserId,
						null,
						Map.of("attachmentId", attachment.id())));
	}

	public PresignedUploadResponse createPresignedUpload(UUID actorUserId, PresignedUploadRequest request) {
		findActiveUser(actorUserId);
		Conversation conversation = conversationService.findAccessibleConversation(actorUserId, request.conversationId());
		String originalFileName = safeOriginalFileName(request.originalFileName());
		String extension = extension(originalFileName);
		String fileType = resolveFileType(request.fileType(), extension);
		long fileSize = request.fileSize();
		validateDeclaredMetadata(fileSize, extension, fileType, request.mimeType());
		if (!fileStorageService.isAvailable()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment upload is temporarily unavailable");
		}
		UUID attachmentId = UUID.randomUUID();
		Instant now = Instant.now();
		String storageKey = quarantineStorageKey(attachmentId, now);
		MessageAttachment attachment = new MessageAttachment(
				attachmentId,
				null,
				actorUserId,
				conversation.id(),
				originalFileName,
				storageKey,
				"/api/attachments/" + attachmentId + "/download",
				normalizeMimeType(request.mimeType()),
				fileType,
				fileSize,
				null,
				"PENDING",
				null,
				now,
				now,
				"INIT");
		messageAttachmentRepository.save(attachment);
		try {
			PresignedObjectRequest presigned = fileStorageService.presignedUpload(
					storageKey,
					attachment.mimeType(),
					fileSize,
					storageProperties.uploadUrlTtl());
			counter("storage_upload", "authorized", fileType).increment();
			return new PresignedUploadResponse(
					attachmentId,
					presigned.url(),
					presigned.headers(),
					presigned.expiresAt());
		} catch (IOException exception) {
			messageAttachmentRepository.markStorageFailed(attachmentId, Instant.now());
			storageFailure("presign_upload", exception);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment upload is temporarily unavailable");
		}
	}

	public AttachmentResponse completePresignedUpload(UUID actorUserId, main.com.chat.wechat.attachment.dto.AttachmentMetadataRequest request) {
		findActiveUser(actorUserId);
		MessageAttachment pending = messageAttachmentRepository.findPendingUpload(request.attachmentId(), actorUserId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pending attachment not found"));
		StoredObject stored;
		try {
			stored = fileStorageService.head(pending.storageKey());
		} catch (IOException exception) {
			storageFailure("head", exception);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment upload is temporarily unavailable");
		}
		try {
			if (stored.size() <= 0 || stored.size() > attachmentProperties.maxSizeFor(pending.fileType())) {
				throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment is larger than allowed for " + pending.fileType());
			}
			if (stored.size() != pending.fileSize()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded object size does not match the upload request");
			}
			ValidatedFile validated = validateStoredObject(pending, stored);
			if (StringUtils.hasText(request.checksum()) && !validated.checksum().equalsIgnoreCase(request.checksum().trim())) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded object checksum does not match");
			}
			Instant now = Instant.now();
			messageAttachmentRepository.finalizeReady(
					pending.id(),
					validated.checksum(),
					validated.dimensions() == null ? null : validated.dimensions().width(),
					validated.dimensions() == null ? null : validated.dimensions().height(),
					now);
			counter("storage_upload", "ready", pending.fileType()).increment();
			return AttachmentResponse.from(messageAttachmentRepository.findById(pending.id()).orElseThrow());
		} catch (ApiException exception) {
			messageAttachmentRepository.markStorageFailed(pending.id(), Instant.now());
			deleteStoredFileQuietly(pending.storageKey());
			counter("storage_validation", "rejected", pending.fileType()).increment();
			throw exception;
		}
	}

	private MessageAttachment findAccessibleAttachment(UUID actorUserId, UUID attachmentId) {
		return messageAttachmentRepository.findAccessibleById(attachmentId, actorUserId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attachment not found"));
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User account is not active"));
	}

	private ValidatedFile validateFile(MultipartFile file, String requestedFileType) {
		if (file == null || file.isEmpty() || file.getSize() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "File is required");
		}
		String originalFileName = safeOriginalFileName(file.getOriginalFilename());
		String extension = extension(originalFileName);
		String fileType = resolveFileType(requestedFileType, extension);
		String declaredMimeType = normalizeMimeType(file.getContentType());
		validateDeclaredMetadata(file.getSize(), extension, fileType, declaredMimeType);
		ValidatedContent content = validateContent(
				originalFileName,
				extension,
				fileType,
				declaredMimeType,
				file.getSize(),
				() -> file.getInputStream(),
				HttpStatus.BAD_REQUEST);
		String mimeType = content.mimeType();
		String checksum = content.checksum();
		ImageDimensions dimensions = content.dimensions();
		return new ValidatedFile(originalFileName, extension, mimeType, fileType, checksum, dimensions);
	}

	private ValidatedFile validateStoredObject(MessageAttachment attachment, StoredObject stored) {
		String extension = extension(attachment.originalFileName());
		ValidatedContent content = validateContent(
				attachment.originalFileName(),
				extension,
				attachment.fileType(),
				attachment.mimeType(),
				stored.size(),
				() -> fileStorageService.openStream(attachment.storageKey()),
				HttpStatus.SERVICE_UNAVAILABLE);
		return new ValidatedFile(
				attachment.originalFileName(),
				extension,
				content.mimeType(),
				attachment.fileType(),
				content.checksum(),
				content.dimensions());
	}

	private ValidatedContent validateContent(
			String originalFileName,
			String extension,
			String fileType,
			String declaredMimeType,
			long fileSize,
			ContentSource source,
			HttpStatus readFailureStatus) {
		validateDeclaredMetadata(fileSize, extension, fileType, declaredMimeType);
		String magicMimeType = magicMimeType(source, readFailureStatus);
		String mimeType = resolveMimeType(declaredMimeType, extension, fileType, magicMimeType);
		String checksum = checksum(source, readFailureStatus);
		ImageDimensions dimensions = imageDimensions(source, fileType, readFailureStatus);
		return new ValidatedContent(mimeType, checksum, dimensions);
	}

	private void validateDeclaredMetadata(long fileSize, String extension, String fileType, String mimeType) {
		if (fileSize <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "File is required");
		}
		if (fileSize > attachmentProperties.maxSizeFor(fileType)) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment is larger than allowed for " + fileType);
		}
		if (!attachmentProperties.allowedExtensionsFor(fileType).contains(extension)) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file extension for " + fileType);
		}
		if (!StringUtils.hasText(mimeType) || !attachmentProperties.allowedMimeTypesFor(fileType).contains(mimeType)) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported MIME type for " + fileType);
		}
	}

	private String safeOriginalFileName(String submittedFileName) {
		if (!StringUtils.hasText(submittedFileName)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Original file name is required");
		}
		String normalized = submittedFileName.trim().replace('\\', '/');
		if (normalized.contains("..") || normalized.contains("\u0000")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name");
		}
		String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
		if (!StringUtils.hasText(baseName) || baseName.length() > 255) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name");
		}
		return baseName;
	}

	private String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		if (dot < 0 || dot == fileName.length() - 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "File extension is required");
		}
		return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private String resolveFileType(String requestedFileType, String extension) {
		if (StringUtils.hasText(requestedFileType)) {
			String normalized = requestedFileType.trim().toUpperCase(Locale.ROOT);
			if (!CLIENT_FILE_TYPES.contains(normalized)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported attachment type: " + normalized);
			}
			return normalized;
		}
		if (attachmentProperties.allowedExtensionsFor("IMAGE").contains(extension)) {
			return "IMAGE";
		}
		if (attachmentProperties.allowedExtensionsFor("VOICE").contains(extension)) {
			return "VOICE";
		}
		if (attachmentProperties.allowedExtensionsFor("FILE").contains(extension)) {
			return "FILE";
		}
		throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file extension");
	}

	private String resolveMimeType(String clientMimeType, String extension, String fileType, String magicMimeType) {
		String extensionMimeType = mimeTypeFromExtension(extension);
		if ("IMAGE".equals(fileType) && (magicMimeType == null || !magicMimeType.startsWith("image/"))) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image content");
		}
		if ("application/zip".equals(magicMimeType) && extensionMimeType != null) {
			return extensionMimeType;
		}
		if (("pdf".equals(extension) || Set.of("docx", "xlsx", "pptx").contains(extension)) && magicMimeType == null) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File content does not match its extension");
		}
		if (magicMimeType != null && attachmentProperties.allowedMimeTypesFor(fileType).contains(magicMimeType)) {
			return magicMimeType;
		}
		if (clientMimeType != null && attachmentProperties.allowedMimeTypesFor(fileType).contains(clientMimeType)) {
			return clientMimeType;
		}
		if (extensionMimeType != null) {
			return extensionMimeType;
		}
		throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported MIME type");
	}

	private String normalizeMimeType(String mimeType) {
		if (!StringUtils.hasText(mimeType)) {
			return null;
		}
		return mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
	}

	private String magicMimeType(ContentSource source, HttpStatus readFailureStatus) {
		byte[] header;
		try (InputStream inputStream = source.open()) {
			header = inputStream.readNBytes(16);
		} catch (IOException exception) {
			throw new ApiException(readFailureStatus, "Could not read file content");
		}
		if (header.length >= 4
				&& header[0] == (byte) 0x89
				&& header[1] == 0x50
				&& header[2] == 0x4e
				&& header[3] == 0x47) {
			return "image/png";
		}
		if (header.length >= 3
				&& header[0] == (byte) 0xff
				&& header[1] == (byte) 0xd8
				&& header[2] == (byte) 0xff) {
			return "image/jpeg";
		}
		if (header.length >= 6
				&& header[0] == 0x47
				&& header[1] == 0x49
				&& header[2] == 0x46) {
			return "image/gif";
		}
		if (header.length >= 12
				&& header[0] == 0x52
				&& header[1] == 0x49
				&& header[2] == 0x46
				&& header[3] == 0x46
				&& header[8] == 0x57
				&& header[9] == 0x45
				&& header[10] == 0x42
				&& header[11] == 0x50) {
			return "image/webp";
		}
		if (header.length >= 4 && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
			return "application/pdf";
		}
		if (header.length >= 4 && header[0] == 0x50 && header[1] == 0x4b && header[2] == 0x03 && header[3] == 0x04) {
			return "application/zip";
		}
		return null;
	}

	private String mimeTypeFromExtension(String extension) {
		return switch (extension) {
			case "jpg", "jpeg" -> "image/jpeg";
			case "png" -> "image/png";
			case "webp" -> "image/webp";
			case "gif" -> "image/gif";
			case "mp3" -> "audio/mpeg";
			case "wav" -> "audio/wav";
			case "m4a" -> "audio/mp4";
			case "webm" -> "audio/webm";
			case "pdf" -> "application/pdf";
			case "doc" -> "application/msword";
			case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			case "xls" -> "application/vnd.ms-excel";
			case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
			case "ppt" -> "application/vnd.ms-powerpoint";
			case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
			case "txt" -> "text/plain";
			case "zip" -> "application/zip";
			default -> null;
		};
	}

	private String checksum(ContentSource source, HttpStatus readFailureStatus) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream inputStream = new DigestInputStream(source.open(), digest)) {
				inputStream.transferTo(OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (IOException exception) {
			throw new ApiException(readFailureStatus, "Could not read file content");
		} catch (NoSuchAlgorithmException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Checksum algorithm is unavailable");
		}
	}

	private ImageDimensions imageDimensions(ContentSource source, String fileType, HttpStatus readFailureStatus) {
		if (!"IMAGE".equals(fileType)) {
			return null;
		}
		try (InputStream inputStream = source.open()) {
			BufferedImage image = ImageIO.read(inputStream);
			if (image == null) {
				throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image content");
			}
			return new ImageDimensions(image.getWidth(), image.getHeight());
		} catch (IOException exception) {
			throw new ApiException(readFailureStatus, "Could not read image metadata");
		}
	}

	private String storageKey(UUID conversationId, UUID attachmentId, String extension, Instant now) {
		return "attachments/%s/%s/%s.%s".formatted(
				conversationId,
				STORAGE_DATE_FORMAT.format(now),
				attachmentId,
				extension);
	}

	private String quarantineStorageKey(UUID attachmentId, Instant now) {
		return "attachments/quarantine/%s/%s".formatted(STORAGE_DATE_FORMAT.format(now), attachmentId);
	}

	private void deleteStoredFileQuietly(String storageKey) {
		try {
			fileStorageService.delete(storageKey);
		} catch (IOException exception) {
			storageFailure("delete_cleanup", exception);
		}
	}

	private void storageFailure(String operation, Exception exception) {
		counter("storage_operation", "failure", operation).increment();
		LOGGER.warn("Object storage operation failed operation={} exceptionClass={}", operation, exception.getClass().getName());
	}

	private Counter counter(String operation, String result, String mediaType) {
		return Counter.builder("storage_operations_total")
				.description("Attachment object-storage operations")
				.tag("operation", operation)
				.tag("result", result)
				.tag("mediaType", mediaType == null ? "unknown" : mediaType)
				.register(meterRegistry);
	}

	@FunctionalInterface
	private interface ContentSource {
		InputStream open() throws IOException;
	}

	private record ValidatedFile(
			String originalFileName,
			String extension,
			String mimeType,
			String fileType,
			String checksum,
			ImageDimensions dimensions) {
	}

	private record ValidatedContent(String mimeType, String checksum, ImageDimensions dimensions) {
	}

	private record ImageDimensions(Integer width, Integer height) {
	}

	private record AttachmentAuditValue(UUID conversationId, String fileName, String fileType, Long fileSize) {
	}

	private record AccessDeniedAuditValue(UUID actorUserId, String action) {
	}
}
