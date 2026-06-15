package main.com.chat.wechat.attachment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AttachmentMetadataRequest(
		@NotNull
		UUID attachmentId,

		@NotBlank
		@Size(max = 255)
		String originalFileName,

		@NotBlank
		@Size(max = 120)
		String mimeType,

		@Size(max = 40)
		String fileType,

		@Positive
		Long fileSize,

		@Size(max = 128)
		String checksum) {
}
