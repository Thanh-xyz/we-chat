package main.com.chat.wechat.user.repository;

import main.com.chat.wechat.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private JdbcTemplate jdbcTemplate;

	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		userRepository = new UserRepository(jdbcTemplate);
	}

	@Test
	void updateAccountStatusClearsDeletedAtWhenRestoringToActive() {
		Instant now = Instant.parse("2026-06-05T07:00:00Z");
		User restoredUser = user("ACTIVE", true, null);
		when(jdbcTemplate.queryForObject(eq("select * from users where id = ?"), any(RowMapper.class), eq(USER_ID)))
				.thenReturn(restoredUser);

		User response = userRepository.updateAccountStatus(USER_ID, "ACTIVE", now);

		assertThat(response.active()).isTrue();
		assertThat(response.deletedAt()).isNull();
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).update(
				sqlCaptor.capture(),
				eq("ACTIVE"),
				eq(true),
				eq("ACTIVE"),
				eq(Timestamp.from(now)),
				eq(Timestamp.from(now)),
				eq(USER_ID));
		assertThat(sqlCaptor.getValue()).contains("deleted_at = case when ? = 'DELETED' then ? else null end");
	}

	@Test
	void updateAccountStatusSetsDeletedAtWhenDeletingUser() {
		Instant now = Instant.parse("2026-06-05T07:00:00Z");
		User deletedUser = user("DELETED", false, now);
		when(jdbcTemplate.queryForObject(eq("select * from users where id = ?"), any(RowMapper.class), eq(USER_ID)))
				.thenReturn(deletedUser);

		User response = userRepository.updateAccountStatus(USER_ID, "DELETED", now);

		assertThat(response.active()).isFalse();
		assertThat(response.deletedAt()).isEqualTo(now);
		verify(jdbcTemplate).update(
				any(String.class),
				eq("DELETED"),
				eq(false),
				eq("DELETED"),
				eq(Timestamp.from(now)),
				eq(Timestamp.from(now)),
				eq(USER_ID));
	}

	private User user(String accountStatus, boolean enabled, Instant deletedAt) {
		Instant now = Instant.parse("2026-06-05T07:00:00Z");
		return new User(
				USER_ID,
				"user",
				"user@example.com",
				"hash",
				"User",
				null,
				"OFFLINE",
				"USER",
				enabled,
				accountStatus,
				true,
				0,
				null,
				0,
				null,
				deletedAt,
				now,
				now);
	}
}
