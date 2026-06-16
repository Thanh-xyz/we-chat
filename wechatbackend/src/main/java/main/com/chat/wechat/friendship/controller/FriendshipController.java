package main.com.chat.wechat.friendship.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.friendship.dto.BlockUserRequest;
import main.com.chat.wechat.friendship.dto.BlockUserResponse;
import main.com.chat.wechat.friendship.dto.FriendRequestResponse;
import main.com.chat.wechat.friendship.dto.FriendResponse;
import main.com.chat.wechat.friendship.dto.FriendUserSummary;
import main.com.chat.wechat.friendship.dto.FriendshipSummaryResponse;
import main.com.chat.wechat.friendship.dto.SendFriendRequestRequest;
import main.com.chat.wechat.friendship.service.FriendshipService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
public class FriendshipController {
	private final FriendshipService friendshipService;

	public FriendshipController(FriendshipService friendshipService) {
		this.friendshipService = friendshipService;
	}

	@PostMapping("/requests")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('FRIEND_REQUEST_SEND')")
	public FriendRequestResponse sendRequest(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody SendFriendRequestRequest request) {
		return friendshipService.sendRequest(user.id(), request);
	}

	@GetMapping("/requests/incoming")
	@PreAuthorize("hasAuthority('FRIEND_READ')")
	public List<FriendRequestResponse> incomingRequests(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return friendshipService.incomingRequests(user.id(), limit, offset);
	}

	@GetMapping("/requests/outgoing")
	@PreAuthorize("hasAuthority('FRIEND_READ')")
	public List<FriendRequestResponse> outgoingRequests(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return friendshipService.outgoingRequests(user.id(), limit, offset);
	}

	@PostMapping("/requests/{requestId}/accept")
	@PreAuthorize("hasAuthority('FRIEND_REQUEST_RESPOND')")
	public FriendRequestResponse acceptRequest(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID requestId) {
		return friendshipService.acceptRequest(user.id(), requestId);
	}

	@PostMapping("/requests/{requestId}/decline")
	@PreAuthorize("hasAuthority('FRIEND_REQUEST_RESPOND')")
	public FriendRequestResponse declineRequest(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID requestId) {
		return friendshipService.declineRequest(user.id(), requestId);
	}

	@PostMapping("/requests/{requestId}/cancel")
	@PreAuthorize("hasAuthority('FRIEND_REQUEST_RESPOND')")
	public FriendRequestResponse cancelRequest(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID requestId) {
		return friendshipService.cancelRequest(user.id(), requestId);
	}

	@GetMapping
	@PreAuthorize("hasAuthority('FRIEND_READ')")
	public List<FriendResponse> friends(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return friendshipService.friends(user.id(), q, limit, offset);
	}

	@DeleteMapping("/{friendId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('FRIEND_DELETE')")
	public void unfriend(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID friendId) {
		friendshipService.unfriend(user.id(), friendId);
	}

	@GetMapping("/summary")
	@PreAuthorize("hasAuthority('FRIEND_READ')")
	public FriendshipSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user) {
		return friendshipService.summary(user.id());
	}

	@PostMapping("/block/{userId}")
	@PreAuthorize("hasAuthority('USER_BLOCK')")
	public BlockUserResponse block(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID userId,
			@Valid @RequestBody(required = false) BlockUserRequest request) {
		return friendshipService.block(user.id(), userId, request);
	}

	@DeleteMapping("/block/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('USER_BLOCK')")
	public void unblock(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId) {
		friendshipService.unblock(user.id(), userId);
	}

	@GetMapping("/blocked")
	@PreAuthorize("hasAuthority('FRIEND_READ')")
	public List<FriendUserSummary> blockedUsers(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return friendshipService.blockedUsers(user.id(), limit, offset);
	}
}
