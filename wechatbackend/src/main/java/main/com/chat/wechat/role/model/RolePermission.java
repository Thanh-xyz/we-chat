package main.com.chat.wechat.role.model;

import java.util.UUID;

public record RolePermission(
		UUID roleId,
		UUID permissionId) {
}
