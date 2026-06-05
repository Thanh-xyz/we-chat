package main.com.chat.wechat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AttachmentMetadataRequest(
		@NotBlank
		@Size(max = 255)
		String fileName,

		@NotBlank
		@Size(max = 2048)
		String fileUrl,

		@Size(max = 100)
		String fileType,

		@PositiveOrZero
		Long fileSize) {
}
