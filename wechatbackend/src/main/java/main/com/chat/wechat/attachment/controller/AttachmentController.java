package main.com.chat.wechat.attachment.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.attachment.dto.AttachmentDownloadResponse;
import main.com.chat.wechat.attachment.dto.AttachmentMetadataRequest;
import main.com.chat.wechat.attachment.dto.AttachmentResponse;
import main.com.chat.wechat.attachment.dto.AttachmentUploadResponse;
import main.com.chat.wechat.attachment.dto.PresignedUploadRequest;
import main.com.chat.wechat.attachment.dto.PresignedUploadResponse;
import main.com.chat.wechat.attachment.service.AttachmentService;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {
	private final AttachmentService attachmentService;

	public AttachmentController(AttachmentService attachmentService) {
		this.attachmentService = attachmentService;
	}

	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('ATTACHMENT_UPLOAD')")
	public AttachmentUploadResponse upload(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam UUID conversationId,
			@RequestParam(required = false) String fileType,
			@RequestPart("file") MultipartFile file) {
		return attachmentService.upload(user.id(), conversationId, fileType, file);
	}

	@GetMapping("/{attachmentId}")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public AttachmentResponse get(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID attachmentId) {
		return attachmentService.get(user.id(), attachmentId);
	}

	@GetMapping("/{attachmentId}/download")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public ResponseEntity<Resource> download(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID attachmentId) {
		AttachmentDownloadResponse response = attachmentService.download(user.id(), attachmentId);
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

	@DeleteMapping("/{attachmentId}")
	@PreAuthorize("hasAuthority('ATTACHMENT_UPLOAD')")
	public void delete(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID attachmentId) {
		attachmentService.delete(user.id(), attachmentId);
	}

	@PostMapping("/presigned-upload")
	@PreAuthorize("hasAuthority('ATTACHMENT_UPLOAD')")
	public PresignedUploadResponse presignedUpload(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody PresignedUploadRequest request) {
		return attachmentService.createPresignedUpload(user.id(), request);
	}

	@PostMapping("/complete")
	@PreAuthorize("hasAuthority('ATTACHMENT_UPLOAD')")
	public AttachmentResponse complete(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody AttachmentMetadataRequest request) {
		return attachmentService.completePresignedUpload(user.id(), request);
	}
}
