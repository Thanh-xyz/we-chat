package main.com.chat.wechat.role.service;

import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.common.security.RbacProperties;
import main.com.chat.wechat.role.model.Role;
import main.com.chat.wechat.role.repository.RoleRepository;
import main.com.chat.wechat.role.repository.UserRoleRepository;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserRoleService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final UserRoleRepository userRoleRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final AuditLogService auditLogService;
	private final RbacProperties rbacProperties;

	public UserRoleService(
			UserRepository userRepository,
			RoleRepository roleRepository,
			UserRoleRepository userRoleRepository,
			RefreshTokenRepository refreshTokenRepository,
			AuditLogService auditLogService,
			RbacProperties rbacProperties) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.userRoleRepository = userRoleRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.auditLogService = auditLogService;
		this.rbacProperties = rbacProperties;
	}

	public List<String> findRoleCodes(UUID userId) {
		return userRoleRepository.findRoleCodesByUserId(userId);
	}

	public List<String> findPermissionCodes(UUID userId) {
		return userRoleRepository.findPermissionCodesByUserId(userId);
	}

	@Transactional
	public List<String> assignRole(UUID userId, String roleCode, AuthenticatedUser actor) {
		User user = findUser(userId);
		Role role = findAssignableRole(roleCode, actor);
		List<String> beforeRoles = findRoleCodes(user.id());
		Instant now = Instant.now();
		userRoleRepository.assign(user.id(), role.id(), actor.id(), now);
		afterRoleChange(user.id(), now, "ADMIN_USER_ROLE_ASSIGN", beforeRoles, findRoleCodes(user.id()));
		return findRoleCodes(user.id());
	}

	@Transactional
	public List<String> removeRole(UUID userId, String roleCode, AuthenticatedUser actor) {
		User user = findUser(userId);
		Role role = findAssignableRole(roleCode, actor);
		List<String> beforeRoles = findRoleCodes(user.id());
		Instant now = Instant.now();
		userRoleRepository.remove(user.id(), role.id());
		afterRoleChange(user.id(), now, "ADMIN_USER_ROLE_REMOVE", beforeRoles, findRoleCodes(user.id()));
		return findRoleCodes(user.id());
	}

	@Transactional
	public List<String> replaceRoles(UUID userId, Set<String> roleCodes, AuthenticatedUser actor) {
		User user = findUser(userId);
		List<String> beforeRoles = findRoleCodes(user.id());
		Set<UUID> roleIds = new LinkedHashSet<>();
		for (String roleCode : roleCodes) {
			roleIds.add(findAssignableRole(roleCode, actor).id());
		}
		Instant now = Instant.now();
		userRoleRepository.replace(user.id(), roleIds, actor.id(), now);
		List<String> afterRoles = findRoleCodes(user.id());
		afterRoleChange(user.id(), now, "ADMIN_USER_ROLES_REPLACE", beforeRoles, afterRoles);
		return afterRoles;
	}

	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private Role findAssignableRole(String roleCode, AuthenticatedUser actor) {
		String normalizedRoleCode = normalizeRoleCode(roleCode);
		if (rbacProperties.superAdminRole().equals(normalizedRoleCode)
				&& !actor.roles().contains(rbacProperties.superAdminRole())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only super admin can assign or remove this role");
		}
		return roleRepository.findByCode(normalizedRoleCode)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Role not found: " + normalizedRoleCode));
	}

	private void afterRoleChange(UUID userId, Instant now, String action, List<String> beforeRoles, List<String> afterRoles) {
		userRepository.incrementTokenVersion(userId, now);
		refreshTokenRepository.revokeAllForUser(userId, now);
		auditLogService.log(
				action,
				"USER",
				userId.toString(),
				"{\"roles\":\"" + beforeRoles + "\"}",
				"{\"roles\":\"" + afterRoles + "\"}");
	}

	private String normalizeRoleCode(String roleCode) {
		return roleCode.trim().toUpperCase(Locale.ROOT);
	}
}
