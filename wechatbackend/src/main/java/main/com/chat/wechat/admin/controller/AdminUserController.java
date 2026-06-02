package main.com.chat.wechat.admin.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.admin.dto.AdminUserResponse;
import main.com.chat.wechat.admin.dto.UpdateUserRolesRequest;
import main.com.chat.wechat.admin.dto.UpdateUserStatusRequest;
import main.com.chat.wechat.admin.service.AdminUserService;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('USER_READ')")
	public List<AdminUserResponse> list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String accountStatus,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return adminUserService.list(search, accountStatus, limit, offset);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('USER_READ')")
	public AdminUserResponse get(@PathVariable UUID id) {
		return adminUserService.get(id);
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasAuthority('USER_STATUS_WRITE')")
	public AdminUserResponse updateStatus(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateUserStatusRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return adminUserService.updateStatus(id, request, actor);
	}

	@PutMapping("/{id}/roles")
	@PreAuthorize("hasAuthority('ROLE_ASSIGN')")
	public AdminUserResponse updateRoles(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateUserRolesRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return adminUserService.updateRoles(id, request, actor);
	}
}
