package main.com.chat.wechat.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateMessageRequest(
		@Size(max = 10000)
		String content,

		String messageType,

		UUID replyToMessageId,

		@Valid
		@Size(max = 10)
		List<AttachmentMetadataRequest> attachments) {
}
