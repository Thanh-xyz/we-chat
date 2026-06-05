package main.com.chat.wechat.message.repository;

import main.com.chat.wechat.message.dto.MessageReactionResponse;
import main.com.chat.wechat.message.model.Message;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
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
public class MessageRepository {
	private final JdbcTemplate jdbcTemplate;

	public MessageRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Message save(Message message) {
		jdbcTemplate.update("""
				insert into messages (
				    id, conversation_id, sender_id, content, message_type, reply_to_message_id,
				    edited_at, deleted_at, recalled_at, is_edited, is_recalled, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				message.id(),
				message.conversationId(),
				message.senderId(),
				message.content(),
				message.messageType(),
				message.replyToMessageId(),
				toTimestamp(message.editedAt()),
				toTimestamp(message.deletedAt()),
				toTimestamp(message.recalledAt()),
				message.edited(),
				message.recalled(),
				Timestamp.from(message.createdAt()),
				Timestamp.from(message.updatedAt()));
		return message;
	}

	public Optional<Message> findById(UUID id) {
		try {
			Message message = jdbcTemplate.queryForObject("""
					select *
					from messages
					where id = ? and deleted_at is null
					""", rowMapper(), id);
			return Optional.ofNullable(message);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public boolean existsInConversation(UUID messageId, UUID conversationId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from messages
				where id = ?
				  and conversation_id = ?
				  and deleted_at is null
				  and is_recalled = false
				  and recalled_at is null
				""", Integer.class, messageId, conversationId);
		return count != null && count > 0;
	}

	public boolean existsAnyInConversation(UUID messageId, UUID conversationId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from messages
				where id = ? and conversation_id = ? and deleted_at is null
				""", Integer.class, messageId, conversationId);
		return count != null && count > 0;
	}

	public List<Message> findByConversationId(UUID conversationId, UUID actorUserId, int limit, int offset) {
		return jdbcTemplate.query("""
				select *
				from messages m
				where m.conversation_id = ?
				  and m.deleted_at is null
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = ?
				  )
				order by m.created_at desc
				limit ? offset ?
				""", rowMapper(), conversationId, actorUserId, limit, offset);
	}

	public List<Message> search(UUID conversationId, UUID actorUserId, String query, int limit, int offset) {
		if (query == null || query.isBlank()) {
			return Collections.emptyList();
		}
		String normalizedQuery = "%" + query.trim().toLowerCase() + "%";
		return jdbcTemplate.query("""
				select distinct m.*
				from messages m
				join users sender on sender.id = m.sender_id
				where m.conversation_id = ?
				  and m.deleted_at is null
				  and m.is_recalled = false
				  and m.recalled_at is null
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = ?
				  )
				  and (
				      lower(coalesce(m.content, '')) like ?
				      or lower(coalesce(sender.username, '')) like ?
				      or lower(coalesce(sender.email, '')) like ?
				      or lower(coalesce(sender.display_name, '')) like ?
				  )
				order by m.created_at desc
				limit ? offset ?
				""", rowMapper(), conversationId, actorUserId, normalizedQuery, normalizedQuery, normalizedQuery, normalizedQuery, limit, offset);
	}

	public Message updateContent(UUID messageId, String content, Instant editedAt) {
		jdbcTemplate.update("""
				update messages
				set content = ?, edited_at = ?, is_edited = true, updated_at = ?
				where id = ? and deleted_at is null
				""", content, Timestamp.from(editedAt), Timestamp.from(editedAt), messageId);
		return findById(messageId).orElseThrow();
	}

	public Message recall(UUID messageId, Instant recalledAt) {
		jdbcTemplate.update("""
				update messages
				set content = null,
				    recalled_at = ?,
				    is_recalled = true,
				    updated_at = ?
				where id = ? and deleted_at is null
				""", Timestamp.from(recalledAt), Timestamp.from(recalledAt), messageId);
		return findById(messageId).orElseThrow();
	}

	public void deleteForUser(UUID messageId, UUID userId, Instant deletedAt) {
		jdbcTemplate.update("""
				insert into message_user_deletions (message_id, user_id, deleted_at)
				values (?, ?, ?)
				on conflict (message_id, user_id)
				do update set deleted_at = excluded.deleted_at
				""", messageId, userId, Timestamp.from(deletedAt));
	}

	public void addReaction(UUID messageId, UUID userId, String emoji, Instant createdAt) {
		jdbcTemplate.update("""
				insert into message_reactions (message_id, user_id, emoji, created_at)
				values (?, ?, ?, ?)
				on conflict (message_id, user_id, emoji) do nothing
				""", messageId, userId, emoji, Timestamp.from(createdAt));
	}

	public void deleteReaction(UUID messageId, UUID userId, String emoji) {
		jdbcTemplate.update("""
				delete from message_reactions
				where message_id = ? and user_id = ? and emoji = ?
				""", messageId, userId, emoji);
	}

	public List<MessageReactionResponse> findReactionSummaries(UUID messageId, UUID actorUserId) {
		return jdbcTemplate.query("""
				select emoji, count(*) as reaction_count, bool_or(user_id = ?) as reacted_by_me
				from message_reactions
				where message_id = ?
				group by emoji
				order by reaction_count desc, emoji
				""", reactionRowMapper(), actorUserId, messageId);
	}

	public Map<UUID, List<MessageReactionResponse>> findReactionSummariesByMessageIds(List<UUID> messageIds, UUID actorUserId) {
		if (messageIds == null || messageIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, List<MessageReactionResponse>> result = new LinkedHashMap<>();
		for (UUID messageId : messageIds) {
			result.put(messageId, new ArrayList<>());
		}
		Object[] args = new Object[messageIds.size() + 1];
		args[0] = actorUserId;
		for (int i = 0; i < messageIds.size(); i++) {
			args[i + 1] = messageIds.get(i);
		}
		jdbcTemplate.query("""
				select message_id, emoji, count(*) as reaction_count, bool_or(user_id = ?) as reacted_by_me
				from message_reactions
				where message_id in (%s)
				group by message_id, emoji
				order by message_id, reaction_count desc, emoji
				""".formatted(placeholders(messageIds.size())),
				(RowCallbackHandler) rs -> {
					UUID messageId = rs.getObject("message_id", UUID.class);
					MessageReactionResponse reaction = new MessageReactionResponse(
							rs.getString("emoji"),
							rs.getInt("reaction_count"),
							rs.getBoolean("reacted_by_me"));
					result.computeIfAbsent(messageId, key -> new ArrayList<>()).add(reaction);
				},
				args);
		return result;
	}

	public int countUnread(UUID conversationId, UUID actorUserId, UUID lastReadMessageId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from messages m
				where m.conversation_id = ?
				  and m.deleted_at is null
				  and m.is_recalled = false
				  and m.sender_id <> ?
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = ?
				  )
				  and (
				      cast(? as uuid) is null
				      or m.created_at > (select created_at from messages where id = ?)
				  )
				""", Integer.class, conversationId, actorUserId, actorUserId, lastReadMessageId, lastReadMessageId);
		return count == null ? 0 : count;
	}

	public Map<UUID, Integer> countUnreadByConversationIds(UUID actorUserId, List<UUID> conversationIds) {
		if (conversationIds == null || conversationIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, Integer> result = new LinkedHashMap<>();
		for (UUID conversationId : conversationIds) {
			result.put(conversationId, 0);
		}
		Object[] args = new Object[conversationIds.size() + 2];
		args[0] = actorUserId;
		args[1] = actorUserId;
		for (int i = 0; i < conversationIds.size(); i++) {
			args[i + 2] = conversationIds.get(i);
		}
		jdbcTemplate.query("""
				select c.id as conversation_id, count(m.id) as unread_count
				from conversations c
				join conversation_members cm
				    on cm.conversation_id = c.id
				   and cm.user_id = ?
				   and cm.left_at is null
				left join messages last_read on last_read.id = cm.last_read_message_id
				left join messages m
				    on m.conversation_id = c.id
				   and m.deleted_at is null
				   and m.is_recalled = false
				   and m.sender_id <> ?
				   and (cm.last_read_message_id is null or m.created_at > last_read.created_at)
				   and not exists (
				       select 1
				       from message_user_deletions mud
				       where mud.message_id = m.id and mud.user_id = cm.user_id
				   )
				where c.id in (%s)
				group by c.id
				""".formatted(placeholders(conversationIds.size())),
				(RowCallbackHandler) rs -> result.put(
						rs.getObject("conversation_id", UUID.class),
						rs.getInt("unread_count")),
				args);
		return result;
	}

	private RowMapper<MessageReactionResponse> reactionRowMapper() {
		return (rs, rowNum) -> new MessageReactionResponse(
				rs.getString("emoji"),
				rs.getInt("reaction_count"),
				rs.getBoolean("reacted_by_me"));
	}

	private RowMapper<Message> rowMapper() {
		return (rs, rowNum) -> mapMessage(rs);
	}

	private Message mapMessage(ResultSet rs) throws SQLException {
		return new Message(
				rs.getObject("id", UUID.class),
				rs.getObject("conversation_id", UUID.class),
				rs.getObject("sender_id", UUID.class),
				rs.getString("content"),
				rs.getString("message_type"),
				rs.getObject("reply_to_message_id", UUID.class),
				toInstant(rs, "edited_at"),
				toInstant(rs, "deleted_at"),
				toInstant(rs, "recalled_at"),
				rs.getBoolean("is_edited"),
				rs.getBoolean("is_recalled"),
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
