package main.com.chat.wechat.conversation.dto;

import java.time.Instant;

public record MuteConversationRequest(Instant mutedUntil) {
}
