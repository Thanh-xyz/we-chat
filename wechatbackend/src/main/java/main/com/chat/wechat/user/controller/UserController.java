package main.com.chat.wechat.user.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.user.dto.UpdateProfileRequest;
import main.com.chat.wechat.user.dto.UserResponse;
import main.com.chat.wechat.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return userService.me(user.id());
	}

	@PutMapping("/me")
	public UserResponse updateMe(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateMe(user.id(), request);
	}
}
