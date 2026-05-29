package main.com.chat.wechat.auth.repository;

import main.com.chat.wechat.auth.model.RefreshToken;
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
public class RefreshTokenRepository {
	private final JdbcTemplate jdbcTemplate;

	public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public RefreshToken save(RefreshToken refreshToken) {
		jdbcTemplate.update("""
				insert into refresh_tokens (id, user_id, token_hash, expires_at, revoked_at, created_at, replaced_by_token_hash)
				values (?, ?, ?, ?, ?, ?, ?)
				""",
				refreshToken.id(),
				refreshToken.userId(),
				refreshToken.tokenHash(),
				Timestamp.from(refreshToken.expiresAt()),
				toTimestamp(refreshToken.revokedAt()),
				Timestamp.from(refreshToken.createdAt()),
				refreshToken.replacedByTokenHash());
		return refreshToken;
	}

	public Optional<RefreshToken> findByTokenHash(String tokenHash) {
		try {
			RefreshToken token = jdbcTemplate.queryForObject("""
					select *
					from refresh_tokens
					where token_hash = ?
					""", rowMapper(), tokenHash);
			return Optional.ofNullable(token);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public void revoke(String tokenHash, Instant revokedAt, String replacedByTokenHash) {
		jdbcTemplate.update("""
				update refresh_tokens
				set revoked_at = ?, replaced_by_token_hash = ?
				where token_hash = ? and revoked_at is null
				""", Timestamp.from(revokedAt), replacedByTokenHash, tokenHash);
	}

	public void revokeAllForUser(UUID userId, Instant revokedAt) {
		jdbcTemplate.update("""
				update refresh_tokens
				set revoked_at = ?
				where user_id = ? and revoked_at is null
				""", Timestamp.from(revokedAt), userId);
	}

	private RowMapper<RefreshToken> rowMapper() {
		return (rs, rowNum) -> mapToken(rs);
	}

	private RefreshToken mapToken(ResultSet rs) throws SQLException {
		return new RefreshToken(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("token_hash"),
				toInstant(rs, "expires_at"),
				toInstant(rs, "revoked_at"),
				toInstant(rs, "created_at"),
				rs.getString("replaced_by_token_hash"));
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
