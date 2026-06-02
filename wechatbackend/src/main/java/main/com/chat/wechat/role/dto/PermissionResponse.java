package main.com.chat.wechat.role.dto;

import main.com.chat.wechat.role.model.Permission;

import java.util.UUID;

public record PermissionResponse(
		UUID id,
		String code,
		String name,
		String description) {

	public static PermissionResponse from(Permission permission) {
		return new PermissionResponse(
				permission.id(),
				permission.code(),
				permission.name(),
				permission.description());
	}
}
