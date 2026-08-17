package main.com.chat.wechat.media.controller;

import main.com.chat.wechat.attachment.dto.AttachmentDownloadResponse;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.media.dto.AttachmentPreviewResponse;
import main.com.chat.wechat.media.dto.MediaDetailResponse;
import main.com.chat.wechat.media.dto.MediaGalleryResponse;
import main.com.chat.wechat.media.dto.MediaSearchResponse;
import main.com.chat.wechat.media.dto.MediaStatisticsResponse;
import main.com.chat.wechat.media.service.MediaGalleryService;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MediaController {
	private final MediaGalleryService mediaGalleryService;

	public MediaController(MediaGalleryService mediaGalleryService) {
		this.mediaGalleryService = mediaGalleryService;
	}

	@GetMapping("/conversations/{conversationId}/media")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public MediaGalleryResponse listMedia(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID conversationId,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) UUID uploaderId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Integer offset) {
		return mediaGalleryService.listMedia(user.id(), conversationId, type, from, to, uploaderId, limit, offset);
	}

	@GetMapping("/conversations/{conversationId}/media/images")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public MediaGalleryResponse listImages(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID conversationId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Integer offset) {
		return mediaGalleryService.listImages(user.id(), conversationId, limit, offset);
	}

	@GetMapping("/conversations/{conversationId}/media/files")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public MediaGalleryResponse listFiles(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID conversationId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Integer offset) {
		return mediaGalleryService.listFiles(user.id(), conversationId, limit, offset);
	}

	@GetMapping("/conversations/{conversationId}/media/voices")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public MediaGalleryResponse listVoices(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID conversationId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Integer offset) {
		return mediaGalleryService.listVoices(user.id(), conversationId, limit, offset);
	}

	@GetMapping("/conversations/{conversationId}/media/search")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public MediaSearchResponse searchMedia(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID conversationId,
			@RequestParam(required = false) String fileName,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) UUID uploaderId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Integer offset) {
		return mediaGalleryService.searchMedia(user.id(), conversationId, fileName, category, uploaderId, from, to, limit, offset);
	}

	@GetMapping("/conversations/{conversationId}/media/stats")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public MediaStatisticsResponse statistics(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID conversationId) {
		return mediaGalleryService.statistics(user.id(), conversationId);
	}

	@GetMapping("/media/{attachmentId}")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public MediaDetailResponse detail(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID attachmentId) {
		return mediaGalleryService.detail(user.id(), attachmentId);
	}

	@GetMapping("/media/{attachmentId}/preview")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public AttachmentPreviewResponse preview(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID attachmentId) {
		return mediaGalleryService.preview(user.id(), attachmentId);
	}

	@GetMapping("/media/{attachmentId}/download")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public ResponseEntity<Resource> download(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID attachmentId) {
		AttachmentDownloadResponse response = mediaGalleryService.download(user.id(), attachmentId);
		ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
				.contentType(response.mimeType() == null
						? MediaType.APPLICATION_OCTET_STREAM
						: MediaType.parseMediaType(response.mimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(response.fileName(), StandardCharsets.UTF_8)
						.build()
						.toString());
		if (response.fileSize() != null) {
			builder.contentLength(response.fileSize());
		}
		return builder.body(response.resource());
	}

	@DeleteMapping("/media/{attachmentId}")
	@PreAuthorize("hasAuthority('ATTACHMENT_UPLOAD')")
	public void deleteMedia(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID attachmentId) {
		mediaGalleryService.deleteMedia(user.id(), attachmentId);
	}
}
