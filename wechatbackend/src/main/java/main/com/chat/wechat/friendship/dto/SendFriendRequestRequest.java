package main.com.chat.wechat.friendship.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendFriendRequestRequest(
		@NotNull
		UUID receiverId,
		@Size(max = 255)
		String message) {
}
