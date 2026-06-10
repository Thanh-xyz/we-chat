package main.com.chat.wechat.user.repository;

import main.com.chat.wechat.user.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
	private final JdbcTemplate jdbcTemplate;

	public UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public User save(User user) {
		jdbcTemplate.update("""
				insert into users (
				    id, username, email, password_hash, display_name, avatar_url, status, role, enabled,
				    account_status, email_verified, token_version, last_login_at, failed_login_count,
				    locked_until, deleted_at, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				user.id(),
				user.username(),
				user.email(),
				user.passwordHash(),
				user.displayName(),
				user.avatarUrl(),
				user.status(),
				user.role(),
				user.enabled(),
				user.accountStatus(),
				user.emailVerified(),
				user.tokenVersion(),
				toTimestamp(user.lastLoginAt()),
				user.failedLoginCount(),
				toTimestamp(user.lockedUntil()),
				toTimestamp(user.deletedAt()),
				Timestamp.from(user.createdAt()),
				Timestamp.from(user.updatedAt()));
		return user;
	}

	public boolean existsByUsernameOrEmail(String username, String email) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from users
				where lower(username) = lower(?) or lower(email) = lower(?)
				""", Integer.class, username, email);
		return count != null && count > 0;
	}

	public boolean existsByUsername(String username) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from users
				where lower(username) = lower(?)
				""", Integer.class, username);
		return count != null && count > 0;
	}

	public boolean existsByEmail(String email) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from users
				where lower(email) = lower(?)
				""", Integer.class, email);
		return count != null && count > 0;
	}

	public Optional<User> findByUsernameOrEmail(String identifier) {
		try {
			User user = jdbcTemplate.queryForObject("""
					select *
					from users
					where (lower(username) = lower(?) or lower(email) = lower(?))
					  and deleted_at is null
					limit 1
					""", rowMapper(), identifier, identifier);
			return Optional.ofNullable(user);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Optional<User> findByEmail(String email) {
		try {
			User user = jdbcTemplate.queryForObject("""
					select *
					from users
					where lower(email) = lower(?)
					  and deleted_at is null
					limit 1
					""", rowMapper(), email);
			return Optional.ofNullable(user);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Optional<User> findById(UUID id) {
		try {
			User user = jdbcTemplate.queryForObject("select * from users where id = ? and deleted_at is null", rowMapper(), id);
			return Optional.ofNullable(user);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public List<User> findAllForAdmin(String search, String accountStatus, int limit, int offset) {
		String normalizedSearch = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
		String normalizedStatus = accountStatus == null || accountStatus.isBlank() ? null : accountStatus.trim().toUpperCase();
		return jdbcTemplate.query("""
				select *
				from users
				where deleted_at is null
				  and (cast(? as text) is null or lower(username) like ? or lower(email) like ? or lower(display_name) like ?)
				  and (cast(? as text) is null or account_status = ?)
				order by created_at desc
				limit ? offset ?
				""",
				rowMapper(),
				normalizedSearch,
				normalizedSearch,
				normalizedSearch,
				normalizedSearch,
				normalizedStatus,
				normalizedStatus,
				limit,
				offset);
	}

	public User updateProfile(UUID id, String displayName, String avatarUrl, Instant updatedAt) {
		jdbcTemplate.update("""
				update users
				set display_name = ?, avatar_url = ?, updated_at = ?
				where id = ? and deleted_at is null
				""", displayName, avatarUrl, Timestamp.from(updatedAt), id);
		return findById(id).orElseThrow();
	}

	public User updateAccountStatus(UUID id, String accountStatus, Instant updatedAt) {
		jdbcTemplate.update("""
				update users
				set account_status = ?,
				    enabled = ?,
				    deleted_at = case when ? = 'DELETED' then ? else null end,
				    token_version = token_version + 1,
				    updated_at = ?
				where id = ?
				""",
				accountStatus,
				"ACTIVE".equals(accountStatus),
				accountStatus,
				Timestamp.from(updatedAt),
				Timestamp.from(updatedAt),
				id);
		return findByIdIncludingDeleted(id).orElseThrow();
	}

	public void recordLoginSuccess(UUID id, Instant loginAt) {
		jdbcTemplate.update("""
				update users
				set last_login_at = ?, failed_login_count = 0, locked_until = null, updated_at = ?
				where id = ?
				""", Timestamp.from(loginAt), Timestamp.from(loginAt), id);
	}

	public User recordLoginFailure(UUID id, Instant updatedAt, int maxFailedAttempts, Instant lockUntil) {
		jdbcTemplate.update("""
				update users
				set failed_login_count = failed_login_count + 1,
				    locked_until = case
				        when failed_login_count + 1 >= ? then ?
				        else locked_until
				    end,
				    updated_at = ?
				where id = ?
				""", maxFailedAttempts, Timestamp.from(lockUntil), Timestamp.from(updatedAt), id);
		return findByIdIncludingDeleted(id).orElseThrow();
	}

	public void incrementTokenVersion(UUID id, Instant updatedAt) {
		jdbcTemplate.update("""
				update users
				set token_version = token_version + 1, updated_at = ?
				where id = ?
				""", Timestamp.from(updatedAt), id);
	}

	public void updatePasswordAndIncrementTokenVersion(UUID id, String passwordHash, Instant updatedAt) {
		jdbcTemplate.update("""
				update users
				set password_hash = ?,
				    token_version = token_version + 1,
				    failed_login_count = 0,
				    locked_until = null,
				    updated_at = ?
				where id = ?
				""", passwordHash, Timestamp.from(updatedAt), id);
	}

	public void markEmailVerified(UUID id, Instant updatedAt) {
		jdbcTemplate.update("""
				update users
				set email_verified = true, updated_at = ?
				where id = ? and deleted_at is null
				""", Timestamp.from(updatedAt), id);
	}

	public Optional<User> findByIdIncludingDeleted(UUID id) {
		try {
			User user = jdbcTemplate.queryForObject("select * from users where id = ?", rowMapper(), id);
			return Optional.ofNullable(user);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	private RowMapper<User> rowMapper() {
		return (rs, rowNum) -> mapUser(rs);
	}

	private User mapUser(ResultSet rs) throws SQLException {
		return new User(
				rs.getObject("id", UUID.class),
				rs.getString("username"),
				rs.getString("email"),
				rs.getString("password_hash"),
				rs.getString("display_name"),
				rs.getString("avatar_url"),
				rs.getString("status"),
				rs.getString("role"),
				rs.getBoolean("enabled"),
				rs.getString("account_status"),
				rs.getBoolean("email_verified"),
				rs.getInt("token_version"),
				toInstant(rs, "last_login_at"),
				rs.getInt("failed_login_count"),
				toInstant(rs, "locked_until"),
				toInstant(rs, "deleted_at"),
				toInstant(rs, "created_at"),
				toInstant(rs, "updated_at"));
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
