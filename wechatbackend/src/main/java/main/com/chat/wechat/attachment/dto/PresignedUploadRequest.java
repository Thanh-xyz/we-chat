package main.com.chat.wechat.attachment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PresignedUploadRequest(
		@NotNull
		UUID conversationId,

		@NotBlank
		@Size(max = 255)
		String originalFileName,

		@Size(max = 40)
		String fileType,

		@NotBlank
		@Size(max = 120)
		String mimeType,

		@Positive
		Long fileSize,

		@Size(max = 128)
		String checksum) {
}
