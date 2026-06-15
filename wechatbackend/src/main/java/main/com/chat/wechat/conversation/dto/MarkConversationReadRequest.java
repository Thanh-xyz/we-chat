package main.com.chat.wechat.conversation.dto;

import java.util.UUID;

public record MarkConversationReadRequest(UUID lastReadMessageId) {
}
