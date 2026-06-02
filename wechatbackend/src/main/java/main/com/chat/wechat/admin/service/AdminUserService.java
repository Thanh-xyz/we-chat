package main.com.chat.wechat.admin.service;

import main.com.chat.wechat.admin.dto.AdminUserResponse;
import main.com.chat.wechat.admin.dto.UpdateUserRolesRequest;
import main.com.chat.wechat.admin.dto.UpdateUserStatusRequest;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.role.model.Role;
import main.com.chat.wechat.role.repository.RoleRepository;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminUserService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final AuditLogService auditLogService;

	public AdminUserService(
			UserRepository userRepository,
			RoleRepository roleRepository,
			RefreshTokenRepository refreshTokenRepository,
			AuditLogService auditLogService) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.auditLogService = auditLogService;
	}

	public List<AdminUserResponse> list(String search, String accountStatus, int limit, int offset) {
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		return userRepository.findAllForAdmin(search, accountStatus, safeLimit, safeOffset).stream()
				.map(user -> AdminUserResponse.from(user, roleRepository.findRoleCodesByUserId(user.id())))
				.toList();
	}

	public AdminUserResponse get(UUID id) {
		User user = userRepository.findByIdIncludingDeleted(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
		return AdminUserResponse.from(user, roleRepository.findRoleCodesByUserId(user.id()));
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
				"ADMIN_USER_STATUS_UPDATE",
				"USER",
				id.toString(),
				"{\"accountStatus\":\"" + before.accountStatus() + "\"}",
				"{\"accountStatus\":\"" + updated.accountStatus() + "\"}");
		return AdminUserResponse.from(updated, roleRepository.findRoleCodesByUserId(updated.id()));
	}

	@Transactional
	public AdminUserResponse updateRoles(UUID id, UpdateUserRolesRequest request, AuthenticatedUser actor) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
		List<String> beforeRoles = roleRepository.findRoleCodesByUserId(user.id());
		Set<UUID> roleIds = new LinkedHashSet<>();
		for (String roleCode : request.roles()) {
			if ("SUPER_ADMIN".equals(roleCode) && !actor.roles().contains("SUPER_ADMIN")) {
				throw new ApiException(HttpStatus.FORBIDDEN, "Only SUPER_ADMIN can assign SUPER_ADMIN");
			}
			Role role = roleRepository.findByCode(roleCode)
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Role not found: " + roleCode));
			roleIds.add(role.id());
		}
		Instant now = Instant.now();
		roleRepository.replaceUserRoles(id, roleIds, actor.id(), now);
		userRepository.incrementTokenVersion(id, now);
		refreshTokenRepository.revokeAllForUser(id, now);
		List<String> afterRoles = roleRepository.findRoleCodesByUserId(id);
		auditLogService.log(
				"ADMIN_USER_ROLES_UPDATE",
				"USER",
				id.toString(),
				"{\"roles\":\"" + beforeRoles + "\"}",
				"{\"roles\":\"" + afterRoles + "\"}");
		User updated = userRepository.findById(id).orElseThrow();
		return AdminUserResponse.from(updated, afterRoles);
	}
}
