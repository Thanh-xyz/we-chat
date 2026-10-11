package main.com.chat.wechat.attachment.dto;

import java.time.Instant;

public record AttachmentDownloadUrlResponse(
		String downloadUrl,
		Instant expiresAt) {
}
