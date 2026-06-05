package main.com.chat.wechat.message.dto;

public record MessageReactionResponse(
		String emoji,
		int count,
		boolean reactedByMe) {
}
