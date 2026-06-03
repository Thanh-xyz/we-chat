package main.com.chat.wechat.role.service;

import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.role.dto.RoleRequest;
import main.com.chat.wechat.role.dto.RoleResponse;
import main.com.chat.wechat.role.model.Role;
import main.com.chat.wechat.role.repository.RolePermissionRepository;
import main.com.chat.wechat.role.repository.RoleRepository;
import main.com.chat.wechat.role.repository.UserRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RoleService {
	private final RoleRepository roleRepository;
	private final RolePermissionRepository rolePermissionRepository;
	private final UserRoleRepository userRoleRepository;
	private final AuditLogService auditLogService;

	public RoleService(
			RoleRepository roleRepository,
			RolePermissionRepository rolePermissionRepository,
			UserRoleRepository userRoleRepository,
			AuditLogService auditLogService) {
		this.roleRepository = roleRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.userRoleRepository = userRoleRepository;
		this.auditLogService = auditLogService;
	}

	public List<RoleResponse> list() {
		return roleRepository.findAll().stream()
				.map(role -> RoleResponse.from(role, rolePermissionRepository.findPermissionsByRoleId(role.id())))
				.toList();
	}

	@Transactional
	public RoleResponse create(RoleRequest request) {
		String code = normalizeCode(request.code());
		roleRepository.findByCode(code).ifPresent(existing -> {
			throw new ApiException(HttpStatus.CONFLICT, "Role code already exists");
		});
		Instant now = Instant.now();
		Role role = roleRepository.save(new Role(
				UUID.randomUUID(),
				code,
				request.name().trim(),
				trimToNull(request.description()),
				false,
				null,
				now,
				now));
		auditLogService.log("ADMIN_ROLE_CREATE", "ROLE", role.id().toString(), null, "{\"code\":\"" + role.code() + "\"}");
		return RoleResponse.from(role, rolePermissionRepository.findPermissionsByRoleId(role.id()));
	}

	@Transactional
	public RoleResponse update(UUID id, RoleRequest request) {
		Role before = roleRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Role not found"));
		String normalizedCode = normalizeCode(request.code());
		if (before.systemRole() && !before.code().equals(normalizedCode)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "System role code cannot be changed");
		}
		Role updated = roleRepository.update(
				id,
				normalizedCode,
				request.name().trim(),
				trimToNull(request.description()),
				Instant.now());
		auditLogService.log(
				"ADMIN_ROLE_UPDATE",
				"ROLE",
				id.toString(),
				"{\"code\":\"" + before.code() + "\"}",
				"{\"code\":\"" + updated.code() + "\"}");
		return RoleResponse.from(updated, rolePermissionRepository.findPermissionsByRoleId(updated.id()));
	}

	@Transactional
	public void delete(UUID id) {
		Role role = roleRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Role not found"));
		if (role.systemRole()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "System roles cannot be deleted");
		}
		if (userRoleRepository.isRoleAssigned(id)) {
			throw new ApiException(HttpStatus.CONFLICT, "Role is assigned to users");
		}
		roleRepository.softDelete(id, Instant.now());
		auditLogService.log("ADMIN_ROLE_DELETE", "ROLE", id.toString(), "{\"code\":\"" + role.code() + "\"}", null);
	}

	private String normalizeCode(String code) {
		return code.trim().toUpperCase(Locale.ROOT);
	}

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
