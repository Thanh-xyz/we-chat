package main.com.chat.wechat.friendship.repository;

import main.com.chat.wechat.friendship.dto.FriendResponse;
import main.com.chat.wechat.friendship.model.RelationStatus;
import main.com.chat.wechat.user.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class FriendshipRepository {
	private final JdbcTemplate jdbcTemplate;

	public FriendshipRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void createTwoWay(UUID firstUserId, UUID secondUserId, Instant createdAt) {
		insertOrReactivate(firstUserId, secondUserId, createdAt);
		insertOrReactivate(secondUserId, firstUserId, createdAt);
	}

	public boolean existsActive(UUID firstUserId, UUID secondUserId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from friendships
				where user_id = ? and friend_id = ? and status = 'ACTIVE'
				""", Integer.class, firstUserId, secondUserId);
		return count != null && count > 0;
	}

	public List<FriendResponse> findFriends(UUID userId, String query, int limit, int offset) {
		String normalizedQuery = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
		return jdbcTemplate.query("""
				select u.*, f.created_at as friendship_created_at
				from friendships f
				join users u on u.id = f.friend_id
				where f.user_id = ?
				  and f.status = 'ACTIVE'
				  and u.deleted_at is null
				  and u.account_status = 'ACTIVE'
				  and (cast(? as text) is null
				       or lower(u.username) like ?
				       or lower(u.display_name) like ?
				       or lower(u.email) like ?)
				order by u.display_name, u.username
				limit ? offset ?
				""",
				(rs, rowNum) -> FriendResponse.from(mapUser(rs), toInstant(rs, "friendship_created_at")),
				userId,
				normalizedQuery,
				normalizedQuery,
				normalizedQuery,
				normalizedQuery,
				limit,
				offset);
	}

	public void softDeleteTwoWay(UUID firstUserId, UUID secondUserId, Instant deletedAt) {
		jdbcTemplate.update("""
				update friendships
				set status = 'DELETED', deleted_at = ?
				where ((user_id = ? and friend_id = ?) or (user_id = ? and friend_id = ?))
				  and status = 'ACTIVE'
				""", Timestamp.from(deletedAt), firstUserId, secondUserId, secondUserId, firstUserId);
	}

	public long countFriends(UUID userId) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*)
				from friendships
				where user_id = ? and status = 'ACTIVE'
				""", Long.class, userId);
		return count == null ? 0 : count;
	}

	public RelationStatus relationStatus(UUID actorUserId, UUID targetUserId) {
		return relationStatuses(actorUserId, List.of(targetUserId)).getOrDefault(targetUserId, RelationStatus.NONE);
	}

	public Map<UUID, RelationStatus> relationStatuses(UUID actorUserId, List<UUID> targetUserIds) {
		if (targetUserIds == null || targetUserIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, RelationStatus> result = new LinkedHashMap<>();
		targetUserIds.forEach(targetUserId -> result.put(targetUserId, RelationStatus.NONE));
		jdbcTemplate.query("""
				select friend_id
				from friendships
				where user_id = ? and status = 'ACTIVE' and friend_id in (%s)
				""".formatted(placeholders(targetUserIds.size())),
				(RowCallbackHandler) rs -> result.put(rs.getObject("friend_id", UUID.class), RelationStatus.FRIEND),
				argsWithActor(actorUserId, targetUserIds));
		jdbcTemplate.query("""
				select receiver_id
				from friend_requests
				where requester_id = ? and status = 'PENDING' and receiver_id in (%s)
				""".formatted(placeholders(targetUserIds.size())),
				(RowCallbackHandler) rs -> {
					UUID userId = rs.getObject("receiver_id", UUID.class);
					if (result.get(userId) == RelationStatus.NONE) {
						result.put(userId, RelationStatus.OUTGOING_REQUEST);
					}
				},
				argsWithActor(actorUserId, targetUserIds));
		jdbcTemplate.query("""
				select requester_id
				from friend_requests
				where receiver_id = ? and status = 'PENDING' and requester_id in (%s)
				""".formatted(placeholders(targetUserIds.size())),
				(RowCallbackHandler) rs -> {
					UUID userId = rs.getObject("requester_id", UUID.class);
					if (result.get(userId) == RelationStatus.NONE) {
						result.put(userId, RelationStatus.INCOMING_REQUEST);
					}
				},
				argsWithActor(actorUserId, targetUserIds));
		jdbcTemplate.query("""
				select blocked_id
				from user_blocks
				where blocker_id = ? and blocked_id in (%s)
				""".formatted(placeholders(targetUserIds.size())),
				(RowCallbackHandler) rs -> result.put(rs.getObject("blocked_id", UUID.class), RelationStatus.BLOCKED_BY_ME),
				argsWithActor(actorUserId, targetUserIds));
		jdbcTemplate.query("""
				select blocker_id
				from user_blocks
				where blocked_id = ? and blocker_id in (%s)
				""".formatted(placeholders(targetUserIds.size())),
				(RowCallbackHandler) rs -> result.put(rs.getObject("blocker_id", UUID.class), RelationStatus.BLOCKED_ME),
				argsWithActor(actorUserId, targetUserIds));
		return result;
	}

	private void insertOrReactivate(UUID userId, UUID friendId, Instant createdAt) {
		jdbcTemplate.update("""
				insert into friendships (id, user_id, friend_id, status, created_at, deleted_at)
				values (?, ?, ?, 'ACTIVE', ?, null)
				on conflict (user_id, friend_id)
				do update set status = 'ACTIVE',
				              deleted_at = null,
				              created_at = excluded.created_at
				""", UUID.randomUUID(), userId, friendId, Timestamp.from(createdAt));
	}

	private Object[] argsWithActor(UUID actorUserId, List<UUID> targetUserIds) {
		Object[] args = new Object[targetUserIds.size() + 1];
		args[0] = actorUserId;
		for (int i = 0; i < targetUserIds.size(); i++) {
			args[i + 1] = targetUserIds.get(i);
		}
		return args;
	}

	private String placeholders(int count) {
		return String.join(",", Collections.nCopies(count, "?"));
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
