package main.com.chat.wechat.message.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateMessageRequest(
		@Size(max = 10000)
		String content,

		@Pattern(regexp = "TEXT|IMAGE|FILE|VOICE|SYSTEM")
		String messageType,

		UUID replyToMessageId) {
}
