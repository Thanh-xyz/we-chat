package main.com.chat.wechat.auth.repository;

import main.com.chat.wechat.auth.model.PasswordResetToken;
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
public class PasswordResetTokenRepository {
	private final JdbcTemplate jdbcTemplate;

	public PasswordResetTokenRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public PasswordResetToken save(PasswordResetToken token) {
		jdbcTemplate.update("""
				insert into password_reset_tokens (id, user_id, token_hash, expires_at, used_at, created_at)
				values (?, ?, ?, ?, ?, ?)
				""",
				token.id(),
				token.userId(),
				token.tokenHash(),
				Timestamp.from(token.expiresAt()),
				toTimestamp(token.usedAt()),
				Timestamp.from(token.createdAt()));
		return token;
	}

	public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
		try {
			PasswordResetToken token = jdbcTemplate.queryForObject("""
					select *
					from password_reset_tokens
					where token_hash = ?
					""", rowMapper(), tokenHash);
			return Optional.ofNullable(token);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public void markUsed(String tokenHash, Instant usedAt) {
		jdbcTemplate.update("""
				update password_reset_tokens
				set used_at = ?
				where token_hash = ? and used_at is null
				""", Timestamp.from(usedAt), tokenHash);
	}

	public void invalidateUnusedForUser(UUID userId, Instant usedAt) {
		jdbcTemplate.update("""
				update password_reset_tokens
				set used_at = ?
				where user_id = ? and used_at is null
				""", Timestamp.from(usedAt), userId);
	}

	private RowMapper<PasswordResetToken> rowMapper() {
		return (rs, rowNum) -> mapToken(rs);
	}

	private PasswordResetToken mapToken(ResultSet rs) throws SQLException {
		return new PasswordResetToken(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("token_hash"),
				toInstant(rs, "expires_at"),
				toInstant(rs, "used_at"),
				toInstant(rs, "created_at"));
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
