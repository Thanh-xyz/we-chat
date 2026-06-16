package main.com.chat.wechat.user.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.friendship.dto.PublicUserSearchResponse;
import main.com.chat.wechat.friendship.service.FriendshipService;
import main.com.chat.wechat.user.dto.UpdateProfileRequest;
import main.com.chat.wechat.user.dto.UserResponse;
import main.com.chat.wechat.user.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
	private final UserService userService;
	private final FriendshipService friendshipService;

	public UserController(UserService userService, FriendshipService friendshipService) {
		this.userService = userService;
		this.friendshipService = friendshipService;
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

	@GetMapping("/search")
	@PreAuthorize("hasAuthority('FRIEND_READ')")
	public List<PublicUserSearchResponse> search(
			@AuthenticationPrincipal AuthenticatedUser user,
			@org.springframework.web.bind.annotation.RequestParam String q,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int offset) {
		return friendshipService.searchUsers(user.id(), q, limit, offset);
	}
}
