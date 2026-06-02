package main.com.chat.wechat.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(
		@NotBlank
		@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$")
		String code,

		@NotBlank
		@Size(max = 120)
		String name,

		@Size(max = 1000)
		String description) {
}
