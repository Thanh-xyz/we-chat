package main.com.chat.wechat.attachment.dto;

import org.springframework.core.io.Resource;

public record AttachmentDownloadResponse(
		Resource resource,
		String fileName,
		String mimeType,
		Long fileSize) {
}
