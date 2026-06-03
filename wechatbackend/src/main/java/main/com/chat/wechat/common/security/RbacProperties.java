package main.com.chat.wechat.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rbac")
public record RbacProperties(
		String defaultUserRole,
		String superAdminRole) {

	public RbacProperties {
		if (defaultUserRole == null || defaultUserRole.isBlank()) {
			defaultUserRole = "USER";
		}
		if (superAdminRole == null || superAdminRole.isBlank()) {
			superAdminRole = "SUPER_ADMIN";
		}
	}
}
