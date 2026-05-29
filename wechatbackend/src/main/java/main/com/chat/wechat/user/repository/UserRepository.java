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
				insert into users (id, username, email, password_hash, display_name, avatar_url, status, role, enabled, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

	public Optional<User> findByUsernameOrEmail(String identifier) {
		try {
			User user = jdbcTemplate.queryForObject("""
					select *
					from users
					where lower(username) = lower(?) or lower(email) = lower(?)
					limit 1
					""", rowMapper(), identifier, identifier);
			return Optional.ofNullable(user);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Optional<User> findById(UUID id) {
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
				toInstant(rs, "created_at"),
				toInstant(rs, "updated_at"));
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
