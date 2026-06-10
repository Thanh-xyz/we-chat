package main.com.chat.wechat.auth.repository;

import main.com.chat.wechat.auth.model.EmailVerificationToken;
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
public class EmailVerificationTokenRepository {
	private final JdbcTemplate jdbcTemplate;

	public EmailVerificationTokenRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public EmailVerificationToken save(EmailVerificationToken token) {
		jdbcTemplate.update("""
				insert into email_verification_tokens (id, user_id, token_hash, expires_at, used_at, created_at)
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

	public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
		try {
			EmailVerificationToken token = jdbcTemplate.queryForObject("""
					select *
					from email_verification_tokens
					where token_hash = ?
					""", rowMapper(), tokenHash);
			return Optional.ofNullable(token);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Optional<Instant> findLatestCreatedAtByUserId(UUID userId) {
		try {
			Timestamp timestamp = jdbcTemplate.queryForObject("""
					select created_at
					from email_verification_tokens
					where user_id = ?
					order by created_at desc
					limit 1
					""", Timestamp.class, userId);
			return Optional.ofNullable(timestamp).map(Timestamp::toInstant);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public void markUsed(String tokenHash, Instant usedAt) {
		jdbcTemplate.update("""
				update email_verification_tokens
				set used_at = ?
				where token_hash = ? and used_at is null
				""", Timestamp.from(usedAt), tokenHash);
	}

	public void invalidateUnusedForUser(UUID userId, Instant usedAt) {
		jdbcTemplate.update("""
				update email_verification_tokens
				set used_at = ?
				where user_id = ? and used_at is null
				""", Timestamp.from(usedAt), userId);
	}

	private RowMapper<EmailVerificationToken> rowMapper() {
		return (rs, rowNum) -> mapToken(rs);
	}

	private EmailVerificationToken mapToken(ResultSet rs) throws SQLException {
		return new EmailVerificationToken(
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
