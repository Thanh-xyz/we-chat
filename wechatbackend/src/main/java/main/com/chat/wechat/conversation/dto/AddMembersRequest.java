package main.com.chat.wechat.conversation.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

public record AddMembersRequest(
		@NotEmpty
		Set<UUID> userIds) {
}
