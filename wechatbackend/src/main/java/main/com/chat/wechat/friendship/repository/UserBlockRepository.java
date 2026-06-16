package main.com.chat.wechat.friendship.repository;

import main.com.chat.wechat.friendship.dto.BlockUserResponse;
import main.com.chat.wechat.friendship.dto.FriendUserSummary;
import main.com.chat.wechat.friendship.model.UserBlock;
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
public class UserBlockRepository {
	private final JdbcTemplate jdbcTemplate;

	public UserBlockRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public UserBlock block(UUID blockerId, UUID blockedId, String reason, Instant createdAt) {
		jdbcTemplate.update("""
				insert into user_blocks (id, blocker_id, blocked_id, reason, created_at)
				values (?, ?, ?, ?, ?)
				on conflict (blocker_id, blocked_id)
				do update set reason = excluded.reason
				""", UUID.randomUUID(), blockerId, blockedId, reason, Timestamp.from(createdAt));
		return findByBlockerAndBlocked(blockerId, blockedId).orElseThrow();
	}

	public Optional<UserBlock> findByBlockerAndBlocked(UUID blockerId, UUID blockedId) {
		try {
			UserBlock block = jdbcTemplate.queryForObject("""
					select *
					from user_blocks
					where blocker_id = ? and blocked_id = ?
					""", rowMapper(), blockerId, blockedId);
			return Optional.ofNullable(block);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public boolean unblock(UUID blockerId, UUID blockedId) {
		return jdbcTemplate.update("""
				delete from user_blocks
				where blocker_id = ? and blocked_id = ?
				""", blockerId, blockedId) > 0;
	}

	public boolean existsBlockBetween(UUID firstUserId, UUID secondUserId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from user_blocks
				where (blocker_id = ? and blocked_id = ?) or (blocker_id = ? and blocked_id = ?)
				""", Integer.class, firstUserId, secondUserId, secondUserId, firstUserId);
		return count != null && count > 0;
	}

	public boolean existsBlockedBy(UUID blockerId, UUID blockedId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from user_blocks
				where blocker_id = ? and blocked_id = ?
				""", Integer.class, blockerId, blockedId);
		return count != null && count > 0;
	}

	public List<FriendUserSummary> findBlockedUsers(UUID blockerId, int limit, int offset) {
		return jdbcTemplate.query("""
				select u.*
				from user_blocks ub
				join users u on u.id = ub.blocked_id
				where ub.blocker_id = ?
				order by ub.created_at desc
				limit ? offset ?
				""", (rs, rowNum) -> FriendUserSummary.from(mapUser(rs)), blockerId, limit, offset);
	}

	public long countBlocked(UUID blockerId) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*)
				from user_blocks
				where blocker_id = ?
				""", Long.class, blockerId);
		return count == null ? 0 : count;
	}

	private RowMapper<UserBlock> rowMapper() {
		return (rs, rowNum) -> new UserBlock(
				rs.getObject("id", UUID.class),
				rs.getObject("blocker_id", UUID.class),
				rs.getObject("blocked_id", UUID.class),
				rs.getString("reason"),
				toInstant(rs, "created_at"));
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

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
