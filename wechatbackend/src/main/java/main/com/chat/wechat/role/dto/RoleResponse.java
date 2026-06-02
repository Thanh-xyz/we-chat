package main.com.chat.wechat.role.dto;

import main.com.chat.wechat.role.model.Permission;
import main.com.chat.wechat.role.model.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
		UUID id,
		String code,
		String name,
		String description,
		boolean systemRole,
		List<PermissionResponse> permissions,
		Instant createdAt,
		Instant updatedAt) {

	public static RoleResponse from(Role role, List<Permission> permissions) {
		return new RoleResponse(
				role.id(),
				role.code(),
				role.name(),
				role.description(),
				role.systemRole(),
				permissions.stream().map(PermissionResponse::from).toList(),
				role.createdAt(),
				role.updatedAt());
	}
}
