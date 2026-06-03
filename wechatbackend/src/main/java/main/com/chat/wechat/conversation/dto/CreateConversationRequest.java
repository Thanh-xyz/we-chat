package main.com.chat.wechat.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateConversationRequest(
		@NotBlank
		@Pattern(regexp = "DIRECT|GROUP")
		String type,

		@Size(max = 100)
		String name,

		@Size(max = 2048)
		String avatarUrl,

		@NotEmpty
		Set<UUID> memberIds) {
}
