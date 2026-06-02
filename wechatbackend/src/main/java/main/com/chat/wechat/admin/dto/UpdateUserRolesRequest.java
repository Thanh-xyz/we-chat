package main.com.chat.wechat.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record UpdateUserRolesRequest(
		@NotEmpty
		Set<@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$") String> roles) {
}
