package main.com.chat.wechat.presence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PresenceRepository {
	private static final int MAX_AUDIENCE = 5_000;

	private final JdbcTemplate jdbcTemplate;

	public PresenceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Updates the presence column only when it changes; returns true when a row changed. */
	public boolean updateStatus(UUID userId, String status) {
		return jdbcTemplate.update("""
				update users
				set status = ?
				where id = ? and deleted_at is null and status is distinct from ?
				""", status, userId, status) > 0;
	}

	public List<UUID> findUserIdsWithStatus(String status) {
		return jdbcTemplate.queryForList("""
				select id
				from users
				where status = ? and deleted_at is null
				""", UUID.class, status);
	}

	/** Friends and people sharing an active conversation: everyone who can see this user's presence. */
	public List<UUID> findAudience(UUID userId) {
		return jdbcTemplate.queryForList("""
				select friend_id as user_id
				from friendships
				where user_id = ? and status = 'ACTIVE'
				union
				select other.user_id
				from conversation_members mine
				join conversation_members other
				  on other.conversation_id = mine.conversation_id
				 and other.left_at is null
				 and other.user_id <> mine.user_id
				where mine.user_id = ? and mine.left_at is null
				limit ?
				""", UUID.class, userId, userId, MAX_AUDIENCE);
	}
}
