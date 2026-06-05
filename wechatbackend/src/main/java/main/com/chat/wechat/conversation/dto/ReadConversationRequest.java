package main.com.chat.wechat.conversation.dto;

import java.util.UUID;

public record ReadConversationRequest(UUID lastReadMessageId) {
}
