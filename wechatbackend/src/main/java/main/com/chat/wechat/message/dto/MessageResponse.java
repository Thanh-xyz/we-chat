package main.com.chat.wechat.message.dto;

import main.com.chat.wechat.message.model.Message;
import main.com.chat.wechat.message.model.MessageAttachment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
		UUID id,
		UUID conversationId,
		UUID senderId,
		String content,
		String messageType,
		UUID replyToMessageId,
		Instant editedAt,
		Instant recalledAt,
		boolean edited,
		boolean recalled,
		List<MessageReactionResponse> reactions,
		List<AttachmentMetadataResponse> attachments,
		Instant createdAt,
		Instant updatedAt) {

	public static MessageResponse from(Message message) {
		return from(message, List.of(), List.of());
	}

	public static MessageResponse from(Message message, List<MessageReactionResponse> reactions) {
		return from(message, reactions, List.of());
	}

	public static MessageResponse from(
			Message message,
			List<MessageReactionResponse> reactions,
			List<MessageAttachment> attachments) {
		boolean recalled = message.recalled() || message.recalledAt() != null;
		return new MessageResponse(
				message.id(),
				message.conversationId(),
				message.senderId(),
				recalled ? "Tin nhắn đã được thu hồi" : message.content(),
				message.messageType(),
				message.replyToMessageId(),
				message.editedAt(),
				message.recalledAt(),
				message.edited() || message.editedAt() != null,
				recalled,
				reactions,
				attachments.stream()
						.map(MessageResponse::attachmentResponse)
						.toList(),
				message.createdAt(),
				message.updatedAt());
	}

	private static AttachmentMetadataResponse attachmentResponse(MessageAttachment attachment) {
		return new AttachmentMetadataResponse(
				attachment.id(),
				attachment.messageId(),
				attachment.fileName(),
				attachment.fileUrl(),
				attachment.fileType(),
				attachment.fileSize(),
				attachment.createdAt());
	}
}
