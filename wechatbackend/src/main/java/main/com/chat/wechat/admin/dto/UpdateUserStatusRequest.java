package main.com.chat.wechat.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
		@NotBlank
		@Pattern(regexp = "ACTIVE|BLOCKED|DELETED|PENDING")
		String accountStatus) {
}
