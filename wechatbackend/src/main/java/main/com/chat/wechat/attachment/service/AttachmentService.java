package main.com.chat.wechat.attachment.service;

import main.com.chat.wechat.attachment.dto.AttachmentDownloadResponse;
import main.com.chat.wechat.attachment.dto.AttachmentResponse;
import main.com.chat.wechat.attachment.dto.AttachmentUploadResponse;
import main.com.chat.wechat.attachment.dto.PresignedUploadRequest;
import main.com.chat.wechat.attachment.dto.PresignedUploadResponse;
import main.com.chat.wechat.attachment.storage.FileStorageService;
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

@Service
public class AttachmentService {
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

	public AttachmentService(
			ConversationService conversationService,
			MessageAttachmentRepository messageAttachmentRepository,
			UserRepository userRepository,
			FileStorageService fileStorageService,
			AttachmentProperties attachmentProperties,
			AuditLogService auditLogService,
			AuditJsonWriter auditJsonWriter,
			RealtimeEventPublisher realtimeEventPublisher) {
		this.conversationService = conversationService;
		this.messageAttachmentRepository = messageAttachmentRepository;
		this.userRepository = userRepository;
		this.fileStorageService = fileStorageService;
		this.attachmentProperties = attachmentProperties;
		this.auditLogService = auditLogService;
		this.auditJsonWriter = auditJsonWriter;
		this.realtimeEventPublisher = realtimeEventPublisher;
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
				now);
		try {
			messageAttachmentRepository.save(attachment);
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
		return AttachmentUploadResponse.from(attachment);
	}

	public AttachmentResponse get(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		return AttachmentResponse.from(findAccessibleAttachment(actorUserId, attachmentId));
	}

	public AttachmentDownloadResponse download(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		MessageAttachment attachment = findAccessibleAttachment(actorUserId, attachmentId);
		if (!fileStorageService.exists(attachment.storageKey())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Attachment file not found");
		}
		try {
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
			throw new ApiException(HttpStatus.NOT_FOUND, "Attachment file not found");
		}
	}

	@Transactional
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
		deleteStoredFileQuietly(attachment.storageKey());
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
		conversationService.findAccessibleConversation(actorUserId, request.conversationId());
		throw new ApiException(HttpStatus.BAD_REQUEST, "Presigned upload is not supported for local storage");
	}

	public AttachmentResponse completePresignedUpload(UUID actorUserId, main.com.chat.wechat.attachment.dto.AttachmentMetadataRequest request) {
		findActiveUser(actorUserId);
		throw new ApiException(HttpStatus.BAD_REQUEST, "Presigned upload is not supported for local storage");
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
		long maxSize = attachmentProperties.maxSizeFor(fileType);
		if (file.getSize() > maxSize) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment is larger than allowed for " + fileType);
		}
		if (!attachmentProperties.allowedExtensionsFor(fileType).contains(extension)) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file extension for " + fileType);
		}
		String mimeType = resolveMimeType(file, extension, fileType);
		if (!attachmentProperties.allowedMimeTypesFor(fileType).contains(mimeType)) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported MIME type for " + fileType);
		}
		String checksum = checksum(file);
		return new ValidatedFile(originalFileName, extension, mimeType, fileType, checksum);
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

	private String resolveMimeType(MultipartFile file, String extension, String fileType) {
		String clientMimeType = normalizeMimeType(file.getContentType());
		String extensionMimeType = mimeTypeFromExtension(extension);
		String magicMimeType = magicMimeType(file);
		if ("IMAGE".equals(fileType) && (magicMimeType == null || !magicMimeType.startsWith("image/"))) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image content");
		}
		if ("application/zip".equals(magicMimeType) && extensionMimeType != null) {
			return extensionMimeType;
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

	private String magicMimeType(MultipartFile file) {
		byte[] header;
		try (InputStream inputStream = file.getInputStream()) {
			header = inputStream.readNBytes(16);
		} catch (IOException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read file content");
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

	private String checksum(MultipartFile file) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream inputStream = new DigestInputStream(file.getInputStream(), digest)) {
				inputStream.transferTo(OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (IOException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read file content");
		} catch (NoSuchAlgorithmException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Checksum algorithm is unavailable");
		}
	}

	private String storageKey(UUID conversationId, UUID attachmentId, String extension, Instant now) {
		return "attachments/%s/%s/%s.%s".formatted(
				conversationId,
				STORAGE_DATE_FORMAT.format(now),
				attachmentId,
				extension);
	}

	private void deleteStoredFileQuietly(String storageKey) {
		try {
			fileStorageService.delete(storageKey);
		} catch (IOException ignored) {
		}
	}

	private record ValidatedFile(
			String originalFileName,
			String extension,
			String mimeType,
			String fileType,
			String checksum) {
	}

	private record AttachmentAuditValue(UUID conversationId, String fileName, String fileType, Long fileSize) {
	}

	private record AccessDeniedAuditValue(UUID actorUserId, String action) {
	}
}
