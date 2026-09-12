package main.com.chat.wechat.media.service;

import main.com.chat.wechat.attachment.dto.AttachmentDownloadResponse;
import main.com.chat.wechat.attachment.storage.FileStorageService;
import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.service.ConversationService;
import main.com.chat.wechat.media.dto.AttachmentPreviewResponse;
import main.com.chat.wechat.media.dto.MediaDetailResponse;
import main.com.chat.wechat.media.dto.MediaGalleryResponse;
import main.com.chat.wechat.media.dto.MediaSearchResponse;
import main.com.chat.wechat.media.dto.MediaStatisticsResponse;
import main.com.chat.wechat.media.model.MediaCategory;
import main.com.chat.wechat.media.model.MediaItem;
import main.com.chat.wechat.media.repository.MediaRepository;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaGalleryService {
	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 100;

	private final MediaRepository mediaRepository;
	private final ConversationService conversationService;
	private final UserRepository userRepository;
	private final FileStorageService fileStorageService;
	private final AuditLogService auditLogService;
	private final AuditJsonWriter auditJsonWriter;
	private final RealtimeEventPublisher realtimeEventPublisher;

	public MediaGalleryService(
			MediaRepository mediaRepository,
			ConversationService conversationService,
			UserRepository userRepository,
			FileStorageService fileStorageService,
			AuditLogService auditLogService,
			AuditJsonWriter auditJsonWriter,
			RealtimeEventPublisher realtimeEventPublisher) {
		this.mediaRepository = mediaRepository;
		this.conversationService = conversationService;
		this.userRepository = userRepository;
		this.fileStorageService = fileStorageService;
		this.auditLogService = auditLogService;
		this.auditJsonWriter = auditJsonWriter;
		this.realtimeEventPublisher = realtimeEventPublisher;
	}

	public MediaGalleryResponse listMedia(
			UUID actorUserId,
			UUID conversationId,
			String category,
			Instant from,
			Instant to,
			UUID uploaderId,
			Integer limit,
			Integer offset) {
		findActiveUser(actorUserId);
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		int safeLimit = safeLimit(limit);
		int safeOffset = safeOffset(offset);
		return MediaGalleryResponse.from(mediaRepository.list(
				conversationId,
				actorUserId,
				MediaCategory.from(category),
				uploaderId,
				from,
				to,
				safeLimit + 1,
				safeOffset), safeLimit, safeOffset);
	}

	public MediaGalleryResponse listImages(UUID actorUserId, UUID conversationId, Integer limit, Integer offset) {
		return listMedia(actorUserId, conversationId, MediaCategory.IMAGE.name(), null, null, null, limit, offset);
	}

	public MediaGalleryResponse listFiles(UUID actorUserId, UUID conversationId, Integer limit, Integer offset) {
		return listMedia(actorUserId, conversationId, MediaCategory.FILE.name(), null, null, null, limit, offset);
	}

	public MediaGalleryResponse listVoices(UUID actorUserId, UUID conversationId, Integer limit, Integer offset) {
		return listMedia(actorUserId, conversationId, MediaCategory.VOICE.name(), null, null, null, limit, offset);
	}

	public MediaSearchResponse searchMedia(
			UUID actorUserId,
			UUID conversationId,
			String fileName,
			String category,
			UUID uploaderId,
			Instant from,
			Instant to,
			Integer limit,
			Integer offset) {
		findActiveUser(actorUserId);
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		int safeLimit = safeLimit(limit);
		int safeOffset = safeOffset(offset);
		return MediaSearchResponse.from(fileName, category, mediaRepository.search(
				conversationId,
				actorUserId,
				fileName,
				MediaCategory.from(category),
				uploaderId,
				from,
				to,
				safeLimit + 1,
				safeOffset), safeLimit, safeOffset);
	}

	public MediaDetailResponse detail(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		return MediaDetailResponse.from(findAccessibleMedia(actorUserId, attachmentId));
	}

	public AttachmentPreviewResponse preview(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		return AttachmentPreviewResponse.from(findAccessibleMedia(actorUserId, attachmentId));
	}

	@Transactional
	public AttachmentDownloadResponse download(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		MediaItem item = findAccessibleMedia(actorUserId, attachmentId);
		if (!fileStorageService.exists(item.storageKey())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Media file not found");
		}
		try {
			mediaRepository.incrementDownloadCount(item.id());
			auditLogService.logSuccess(
					"MEDIA_DOWNLOAD",
					"ATTACHMENT",
					item.id().toString(),
					null,
					null,
					auditJsonWriter.write(new MediaAuditValue(item.conversationId(), item.messageId(), item.category(), item.fileName(), item.fileSize())));
			return new AttachmentDownloadResponse(
					fileStorageService.download(item.storageKey()),
					item.fileName(),
					item.mimeType(),
					item.fileSize());
		} catch (IOException exception) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Media file not found");
		}
	}

	@Transactional
	public void deleteMedia(UUID actorUserId, UUID attachmentId) {
		findActiveUser(actorUserId);
		MediaItem item = findAccessibleMedia(actorUserId, attachmentId);
		if (!actorUserId.equals(item.uploaderId())) {
			logAccessDenied(actorUserId, attachmentId, "Only the uploader can delete this media");
			throw new ApiException(HttpStatus.FORBIDDEN, "Only the uploader can delete this media");
		}
		mediaRepository.softDelete(item.id(), Instant.now());
		auditLogService.logSuccess(
				"MEDIA_DELETE",
				"ATTACHMENT",
				item.id().toString(),
				auditJsonWriter.write(new MediaAuditValue(item.conversationId(), item.messageId(), item.category(), item.fileName(), item.fileSize())),
				null);
		realtimeEventPublisher.publishToMembersAfterCommit(
				conversationService.memberIds(item.conversationId()),
				RealtimeEvent.of(
						"media.deleted",
						item.conversationId(),
						item.messageId(),
						actorUserId,
						null,
						Map.of(
								"conversationId", item.conversationId(),
								"attachmentId", item.id(),
								"category", item.category().name())));
	}

	public MediaStatisticsResponse statistics(UUID actorUserId, UUID conversationId) {
		findActiveUser(actorUserId);
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		return MediaStatisticsResponse.from(mediaRepository.statistics(conversationId, actorUserId));
	}

	private MediaItem findAccessibleMedia(UUID actorUserId, UUID attachmentId) {
		return mediaRepository.findAccessibleById(attachmentId, actorUserId)
				.orElseThrow(() -> {
					logAccessDenied(actorUserId, attachmentId, "Media not found or not accessible");
					return new ApiException(HttpStatus.NOT_FOUND, "Media not found");
				});
	}

	private void logAccessDenied(UUID actorUserId, UUID attachmentId, String reason) {
		auditLogService.logFailure(
				"MEDIA_ACCESS_DENIED",
				"ATTACHMENT",
				attachmentId == null ? null : attachmentId.toString(),
				reason,
				auditJsonWriter.write(new AccessDeniedAuditValue(actorUserId)));
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User account is not active"));
	}

	private int safeLimit(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		if (limit < 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Limit must be greater than zero");
		}
		return Math.min(limit, MAX_LIMIT);
	}

	private int safeOffset(Integer offset) {
		if (offset == null) {
			return 0;
		}
		if (offset < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Offset must be zero or greater");
		}
		return offset;
	}

	private record MediaAuditValue(UUID conversationId, UUID messageId, MediaCategory category, String fileName, Long fileSize) {
	}

	private record AccessDeniedAuditValue(UUID actorUserId) {
	}
}
