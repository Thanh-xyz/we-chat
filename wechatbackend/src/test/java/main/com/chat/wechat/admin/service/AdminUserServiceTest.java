package main.com.chat.wechat.admin.service;

import main.com.chat.wechat.admin.dto.AdminUserResponse;
import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.role.service.UserRoleService;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
	private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserRoleService userRoleService;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private AuditLogService auditLogService;

	@Mock
	private AuditJsonWriter auditJsonWriter;

	private AdminUserService adminUserService;

	@BeforeEach
	void setUp() {
		adminUserService = new AdminUserService(
				userRepository,
				userRoleService,
				refreshTokenRepository,
				auditLogService,
				auditJsonWriter);
	}

	@Test
	void listLoadsRolesInOneBatchQuery() {
		List<User> users = List.of(activeUser(USER_A), activeUser(USER_B));
		when(userRepository.findAllForAdmin("user", null, 50, 0)).thenReturn(users);
		when(userRoleService.findRoleCodesByUserIds(List.of(USER_A, USER_B)))
				.thenReturn(Map.of(
						USER_A, List.of("USER"),
						USER_B, List.of("ADMIN", "USER")));

		List<AdminUserResponse> responses = adminUserService.list("user", null, 50, 0);

		assertThat(responses).hasSize(2);
		assertThat(responses.get(0).roles()).containsExactly("USER");
		assertThat(responses.get(1).roles()).containsExactly("ADMIN", "USER");
		verify(userRoleService).findRoleCodesByUserIds(List.of(USER_A, USER_B));
		verify(userRoleService, never()).findRoleCodes(USER_A);
		verify(userRoleService, never()).findRoleCodes(USER_B);
	}

	private User activeUser(UUID id) {
		Instant now = Instant.now();
		return new User(
				id,
				"user-" + id,
				id + "@example.com",
				"hash",
				"User",
				null,
				"OFFLINE",
				"USER",
				true,
				"ACTIVE",
				true,
				0,
				null,
				0,
				null,
				null,
				now,
				now);
	}
}
