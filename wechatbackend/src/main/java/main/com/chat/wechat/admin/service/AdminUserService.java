package main.com.chat.wechat.admin.service;

import main.com.chat.wechat.admin.dto.AdminUserResponse;
import main.com.chat.wechat.admin.dto.UpdateUserRolesRequest;
import main.com.chat.wechat.admin.dto.UpdateUserStatusRequest;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.role.service.UserRoleService;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {
	private final UserRepository userRepository;
	private final UserRoleService userRoleService;
	private final RefreshTokenRepository refreshTokenRepository;
	private final AuditLogService auditLogService;

	public AdminUserService(
			UserRepository userRepository,
			UserRoleService userRoleService,
			RefreshTokenRepository refreshTokenRepository,
			AuditLogService auditLogService) {
		this.userRepository = userRepository;
		this.userRoleService = userRoleService;
		this.refreshTokenRepository = refreshTokenRepository;
		this.auditLogService = auditLogService;
	}

	public List<AdminUserResponse> list(String search, String accountStatus, int limit, int offset) {
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		return userRepository.findAllForAdmin(search, accountStatus, safeLimit, safeOffset).stream()
				.map(user -> AdminUserResponse.from(user, userRoleService.findRoleCodes(user.id())))
				.toList();
	}

	public AdminUserResponse get(UUID id) {
		User user = userRepository.findByIdIncludingDeleted(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
		return AdminUserResponse.from(user, userRoleService.findRoleCodes(user.id()));
	}

	@Transactional
	public AdminUserResponse updateStatus(UUID id, UpdateUserStatusRequest request, AuthenticatedUser actor) {
		if (actor.id().equals(id) && !"ACTIVE".equals(request.accountStatus())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Admins cannot disable their own account");
		}
		User before = userRepository.findByIdIncludingDeleted(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
		Instant now = Instant.now();
		User updated = userRepository.updateAccountStatus(id, request.accountStatus(), now);
		refreshTokenRepository.revokeAllForUser(id, now);
		auditLogService.log(
				auditActionForStatus(request.accountStatus()),
				"USER",
				id.toString(),
				"{\"accountStatus\":\"" + before.accountStatus() + "\"}",
				"{\"accountStatus\":\"" + updated.accountStatus() + "\"}");
		return AdminUserResponse.from(updated, userRoleService.findRoleCodes(updated.id()));
	}

	@Transactional
	public AdminUserResponse updateRoles(UUID id, UpdateUserRolesRequest request, AuthenticatedUser actor) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
		List<String> afterRoles = userRoleService.replaceRoles(id, request.roles(), actor);
		User updated = userRepository.findById(user.id()).orElseThrow();
		return AdminUserResponse.from(updated, afterRoles);
	}

	private String auditActionForStatus(String accountStatus) {
		return switch (accountStatus) {
			case "BLOCKED" -> "ADMIN_USER_BLOCK";
			case "ACTIVE" -> "ADMIN_USER_UNBLOCK";
			case "DELETED" -> "ADMIN_USER_SOFT_DELETE";
			default -> "ADMIN_USER_STATUS_UPDATE";
		};
	}
}
