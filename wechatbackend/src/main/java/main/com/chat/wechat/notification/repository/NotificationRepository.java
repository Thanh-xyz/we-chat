package main.com.chat.wechat.notification.repository;

import main.com.chat.wechat.notification.model.Notification;
import main.com.chat.wechat.notification.model.NotificationDelivery;
import main.com.chat.wechat.notification.model.NotificationPreference;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationRepository {
	private final JdbcTemplate jdbcTemplate;

	public NotificationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void saveAll(List<Notification> notifications) {
		if (notifications == null || notifications.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate("""
				insert into notifications (
				    id, user_id, actor_user_id, conversation_id, message_id,
				    type, title, content, is_read, created_at, read_at, deleted_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				notifications,
				notifications.size(),
				(ps, notification) -> {
					ps.setObject(1, notification.id());
					ps.setObject(2, notification.userId());
					ps.setObject(3, notification.actorUserId());
					ps.setObject(4, notification.conversationId());
					ps.setObject(5, notification.messageId());
					ps.setString(6, notification.type());
					ps.setString(7, notification.title());
					ps.setString(8, notification.content());
					ps.setBoolean(9, notification.read());
					ps.setTimestamp(10, Timestamp.from(notification.createdAt()));
					ps.setTimestamp(11, toTimestamp(notification.readAt()));
					ps.setTimestamp(12, toTimestamp(notification.deletedAt()));
				});
	}

	public void saveDeliveries(List<NotificationDelivery> deliveries) {
		if (deliveries == null || deliveries.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate("""
				insert into notification_delivery (
				    id, notification_id, channel, status, sent_at, created_at
				)
				values (?, ?, ?, ?, ?, ?)
				""",
				deliveries,
				deliveries.size(),
				(ps, delivery) -> {
					ps.setObject(1, delivery.id());
					ps.setObject(2, delivery.notificationId());
					ps.setString(3, delivery.channel());
					ps.setString(4, delivery.status());
					ps.setTimestamp(5, toTimestamp(delivery.sentAt()));
					ps.setTimestamp(6, Timestamp.from(delivery.createdAt()));
				});
	}

	public List<Notification> findByUserId(UUID userId, int limit, int offset) {
		return jdbcTemplate.query("""
				select *
				from notifications
				where user_id = ?
				  and deleted_at is null
				order by created_at desc
				limit ? offset ?
				""", notificationRowMapper(), userId, limit, offset);
	}

	public Optional<Notification> findByIdForUser(UUID notificationId, UUID userId) {
		try {
			Notification notification = jdbcTemplate.queryForObject("""
					select *
					from notifications
					where id = ?
					  and user_id = ?
					  and deleted_at is null
					""", notificationRowMapper(), notificationId, userId);
			return Optional.ofNullable(notification);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public int countUnread(UUID userId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from notifications
				where user_id = ?
				  and is_read = false
				  and deleted_at is null
				""", Integer.class, userId);
		return count == null ? 0 : count;
	}

	public Map<UUID, Integer> countUnreadByUserIds(List<UUID> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, Integer> result = new LinkedHashMap<>();
		for (UUID userId : userIds) {
			result.put(userId, 0);
		}
		jdbcTemplate.query("""
				select user_id, count(*) as unread_count
				from notifications
				where user_id in (%s)
				  and is_read = false
				  and deleted_at is null
				group by user_id
				""".formatted(placeholders(userIds.size())),
				(RowCallbackHandler) rs -> result.put(rs.getObject("user_id", UUID.class), rs.getInt("unread_count")),
				userIds.toArray());
		return result;
	}

	public Optional<Notification> markRead(UUID notificationId, UUID userId, Instant readAt) {
		jdbcTemplate.update("""
				update notifications
				set is_read = true,
				    read_at = coalesce(read_at, ?)
				where id = ?
				  and user_id = ?
				  and deleted_at is null
				""", Timestamp.from(readAt), notificationId, userId);
		return findByIdForUser(notificationId, userId);
	}

	public int markAllRead(UUID userId, Instant readAt) {
		return jdbcTemplate.update("""
				update notifications
				set is_read = true,
				    read_at = coalesce(read_at, ?)
				where user_id = ?
				  and is_read = false
				  and deleted_at is null
				""", Timestamp.from(readAt), userId);
	}

	public Optional<Notification> softDelete(UUID notificationId, UUID userId, Instant deletedAt) {
		Optional<Notification> existing = findByIdForUser(notificationId, userId);
		if (existing.isEmpty()) {
			return Optional.empty();
		}
		jdbcTemplate.update("""
				update notifications
				set deleted_at = ?
				where id = ?
				  and user_id = ?
				  and deleted_at is null
				""", Timestamp.from(deletedAt), notificationId, userId);
		return existing;
	}

	public Optional<NotificationPreference> findPreferenceByUserId(UUID userId) {
		try {
			NotificationPreference preference = jdbcTemplate.queryForObject("""
					select *
					from notification_preferences
					where user_id = ?
					""", preferenceRowMapper(), userId);
			return Optional.ofNullable(preference);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Map<UUID, NotificationPreference> findPreferencesByUserIds(List<UUID> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, NotificationPreference> result = new LinkedHashMap<>();
		jdbcTemplate.query("""
				select *
				from notification_preferences
				where user_id in (%s)
				""".formatted(placeholders(userIds.size())),
				(RowCallbackHandler) rs -> {
					NotificationPreference preference = mapPreference(rs);
					result.put(preference.userId(), preference);
				},
				userIds.toArray());
		return result;
	}

	public NotificationPreference saveDefaultPreference(UUID userId, Instant now) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				insert into notification_preferences (
				    id, user_id, message_enabled, mention_enabled, reaction_enabled,
				    group_enabled, system_enabled, email_enabled, push_enabled, created_at, updated_at
				)
				values (?, ?, true, true, true, true, true, false, false, ?, ?)
				on conflict (user_id) do nothing
				""", id, userId, Timestamp.from(now), Timestamp.from(now));
		return findPreferenceByUserId(userId).orElseGet(() -> defaultPreference(userId, now));
	}

	public NotificationPreference updatePreference(NotificationPreference preference) {
		jdbcTemplate.update("""
				insert into notification_preferences (
				    id, user_id, message_enabled, mention_enabled, reaction_enabled,
				    group_enabled, system_enabled, email_enabled, push_enabled, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (user_id)
				do update set
				    message_enabled = excluded.message_enabled,
				    mention_enabled = excluded.mention_enabled,
				    reaction_enabled = excluded.reaction_enabled,
				    group_enabled = excluded.group_enabled,
				    system_enabled = excluded.system_enabled,
				    email_enabled = excluded.email_enabled,
				    push_enabled = excluded.push_enabled,
				    updated_at = excluded.updated_at
				""",
				preference.id(),
				preference.userId(),
				preference.messageEnabled(),
				preference.mentionEnabled(),
				preference.reactionEnabled(),
				preference.groupEnabled(),
				preference.systemEnabled(),
				preference.emailEnabled(),
				preference.pushEnabled(),
				Timestamp.from(preference.createdAt()),
				Timestamp.from(preference.updatedAt()));
		return findPreferenceByUserId(preference.userId()).orElseThrow();
	}

	public NotificationPreference defaultPreference(UUID userId, Instant now) {
		return new NotificationPreference(
				UUID.randomUUID(),
				userId,
				true,
				true,
				true,
				true,
				true,
				false,
				false,
				now,
				now);
	}

	private RowMapper<Notification> notificationRowMapper() {
		return (rs, rowNum) -> mapNotification(rs);
	}

	private RowMapper<NotificationPreference> preferenceRowMapper() {
		return (rs, rowNum) -> mapPreference(rs);
	}

	private Notification mapNotification(ResultSet rs) throws SQLException {
		return new Notification(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getObject("actor_user_id", UUID.class),
				rs.getObject("conversation_id", UUID.class),
				rs.getObject("message_id", UUID.class),
				rs.getString("type"),
				rs.getString("title"),
				rs.getString("content"),
				rs.getBoolean("is_read"),
				toInstant(rs, "created_at"),
				toInstant(rs, "read_at"),
				toInstant(rs, "deleted_at"));
	}

	private NotificationPreference mapPreference(ResultSet rs) throws SQLException {
		return new NotificationPreference(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getBoolean("message_enabled"),
				rs.getBoolean("mention_enabled"),
				rs.getBoolean("reaction_enabled"),
				rs.getBoolean("group_enabled"),
				rs.getBoolean("system_enabled"),
				rs.getBoolean("email_enabled"),
				rs.getBoolean("push_enabled"),
				toInstant(rs, "created_at"),
				toInstant(rs, "updated_at"));
	}

	private String placeholders(int count) {
		return String.join(",", Collections.nCopies(count, "?"));
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
