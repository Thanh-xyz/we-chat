package main.com.chat.wechat.notification.controller;

import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.notification.dto.NotificationCountResponse;
import main.com.chat.wechat.notification.dto.NotificationPreferenceResponse;
import main.com.chat.wechat.notification.dto.NotificationResponse;
import main.com.chat.wechat.notification.dto.NotificationSummaryResponse;
import main.com.chat.wechat.notification.dto.UpdateNotificationPreferenceRequest;
import main.com.chat.wechat.notification.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public NotificationSummaryResponse list(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return notificationService.listNotifications(user.id(), limit, offset);
	}

	@GetMapping("/unread-count")
	@PreAuthorize("isAuthenticated()")
	public NotificationCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
		return notificationService.countUnread(user.id());
	}

	@PostMapping("/{id}/read")
	@PreAuthorize("isAuthenticated()")
	public NotificationResponse markRead(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id) {
		return notificationService.markRead(user.id(), id);
	}

	@PostMapping("/read-all")
	@PreAuthorize("isAuthenticated()")
	public NotificationCountResponse markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
		return notificationService.markAllRead(user.id());
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public void delete(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id) {
		notificationService.deleteNotification(user.id(), id);
	}

	@GetMapping("/preferences")
	@PreAuthorize("isAuthenticated()")
	public NotificationPreferenceResponse getPreference(@AuthenticationPrincipal AuthenticatedUser user) {
		return notificationService.getPreference(user.id());
	}

	@PutMapping("/preferences")
	@PreAuthorize("isAuthenticated()")
	public NotificationPreferenceResponse updatePreference(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody UpdateNotificationPreferenceRequest request) {
		return notificationService.updatePreference(user.id(), request);
	}
}
