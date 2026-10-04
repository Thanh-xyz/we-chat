package main.com.chat.wechat.message.repository;

import main.com.chat.wechat.message.dto.MessageReactionResponse;
import main.com.chat.wechat.message.model.Message;
import main.com.chat.wechat.message.pagination.MessageCursor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MessageRepository {
	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public MessageRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
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

	public boolean existsReadableInConversation(UUID messageId, UUID conversationId, UUID userId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from messages m
				where m.id = ?
				  and m.conversation_id = ?
				  and m.deleted_at is null
				  and m.is_recalled = false
				  and m.recalled_at is null
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = ?
				  )
				""", Integer.class, messageId, conversationId, userId);
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

	public Optional<UUID> findLatestMessageId(UUID conversationId, UUID userId) {
		try {
			UUID messageId = jdbcTemplate.queryForObject("""
					select m.id
					from messages m
					where m.conversation_id = ?
					  and m.deleted_at is null
					  and m.is_recalled = false
					  and m.recalled_at is null
					  and not exists (
					      select 1
					      from message_user_deletions mud
					      where mud.message_id = m.id and mud.user_id = ?
					  )
					order by m.created_at desc
					limit 1
					""", UUID.class, conversationId, userId);
			return Optional.ofNullable(messageId);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Optional<Instant> findCreatedAt(UUID messageId) {
		try {
			Timestamp createdAt = jdbcTemplate.queryForObject("""
					select created_at
					from messages
					where id = ? and deleted_at is null
					""", Timestamp.class, messageId);
			return createdAt == null ? Optional.empty() : Optional.of(createdAt.toInstant());
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public List<Message> findByConversationId(
			UUID conversationId,
			UUID actorUserId,
			MessageCursor cursor,
			int limit) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
				.addValue("conversationId", conversationId)
				.addValue("actorUserId", actorUserId)
				.addValue("limit", limit);
		String cursorPredicate = cursorPredicate(cursor, parameters);
		return namedParameterJdbcTemplate.query("""
				select *
				from messages m
				where m.conversation_id = :conversationId
				  and m.deleted_at is null
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = :actorUserId
				  )
				""" + cursorPredicate + """
				order by m.created_at desc, m.id desc
				limit :limit
				""", parameters, rowMapper());
	}

	public List<Message> search(UUID conversationId, UUID actorUserId, String query, MessageCursor cursor, int limit) {
		if (query == null || query.isBlank()) {
			return Collections.emptyList();
		}
		String normalizedQuery = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
		Map<UUID, Message> matches = new LinkedHashMap<>();
		searchByContent(conversationId, actorUserId, normalizedQuery, cursor, limit)
				.forEach(message -> matches.putIfAbsent(message.id(), message));
		searchBySender(conversationId, actorUserId, normalizedQuery, cursor, limit)
				.forEach(message -> matches.putIfAbsent(message.id(), message));

		List<Message> orderedMatches = new ArrayList<>(matches.values());
		orderedMatches.sort(Comparator
				.comparing(Message::createdAt, Comparator.reverseOrder())
				.thenComparing(Message::id, Comparator.reverseOrder()));
		return orderedMatches.size() <= limit
				? orderedMatches
				: orderedMatches.subList(0, limit);
	}

	private List<Message> searchByContent(
			UUID conversationId,
			UUID actorUserId,
			String normalizedQuery,
			MessageCursor cursor,
			int limit) {
		MapSqlParameterSource parameters = searchParameters(conversationId, actorUserId, normalizedQuery, limit);
		String cursorPredicate = cursorPredicate(cursor, parameters);
		return namedParameterJdbcTemplate.query("""
				select m.*
				from messages m
				where m.conversation_id = :conversationId
				  and m.deleted_at is null
				  and m.is_recalled = false
				  and m.recalled_at is null
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = :actorUserId
				  )
				  and lower(m.content) like :query
				""" + cursorPredicate + """
				order by m.created_at desc, m.id desc
				limit :limit
				""", parameters, rowMapper());
	}

	private List<Message> searchBySender(
			UUID conversationId,
			UUID actorUserId,
			String normalizedQuery,
			MessageCursor cursor,
			int limit) {
		MapSqlParameterSource parameters = searchParameters(conversationId, actorUserId, normalizedQuery, limit);
		String cursorPredicate = cursorPredicate(cursor, parameters);
		return namedParameterJdbcTemplate.query("""
				select m.*
				from messages m
				join users sender on sender.id = m.sender_id
				  and sender.deleted_at is null
				where m.conversation_id = :conversationId
				  and m.deleted_at is null
				  and m.is_recalled = false
				  and m.recalled_at is null
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = :actorUserId
				  )
				  and (
				      lower(sender.username) like :query
				      or lower(sender.email) like :query
				      or lower(sender.display_name) like :query
				  )
				""" + cursorPredicate + """
				order by m.created_at desc, m.id desc
				limit :limit
				""", parameters, rowMapper());
	}

	private MapSqlParameterSource searchParameters(
			UUID conversationId,
			UUID actorUserId,
			String normalizedQuery,
			int limit) {
		return new MapSqlParameterSource()
				.addValue("conversationId", conversationId)
				.addValue("actorUserId", actorUserId)
				.addValue("query", normalizedQuery)
				.addValue("limit", limit);
	}

	private String cursorPredicate(MessageCursor cursor, MapSqlParameterSource parameters) {
		if (cursor == null) {
			return "";
		}
		parameters.addValue("cursorCreatedAt", Timestamp.from(cursor.createdAt()));
		parameters.addValue("cursorId", cursor.id());
		return """
				and (
				    m.created_at < :cursorCreatedAt
				    or (m.created_at = :cursorCreatedAt and m.id < :cursorId)
				)
				""";
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
				  and m.recalled_at is null
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

	public int countUnreadAfter(UUID conversationId, UUID actorUserId, Instant lastReadAt) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from messages m
				where m.conversation_id = ?
				  and m.deleted_at is null
				  and m.is_recalled = false
				  and m.recalled_at is null
				  and m.sender_id <> ?
				  and m.created_at > coalesce(?, timestamp '1970-01-01 00:00:00')
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = ?
				  )
				""", Integer.class, conversationId, actorUserId, toTimestamp(lastReadAt), actorUserId);
		return count == null ? 0 : count;
	}

	public Map<UUID, Integer> countUnreadByConversationIds(UUID actorUserId, Collection<UUID> conversationIds) {
		if (conversationIds == null || conversationIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, Integer> result = new LinkedHashMap<>();
		Object[] args = new Object[conversationIds.size() + 2];
		args[0] = actorUserId;
		args[1] = actorUserId;
		int argumentIndex = 2;
		for (UUID conversationId : conversationIds) {
			args[argumentIndex++] = conversationId;
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
				   and m.recalled_at is null
				   and m.sender_id <> ?
				   and m.created_at > coalesce(cm.last_read_at, last_read.created_at, timestamp '1970-01-01 00:00:00')
				   and not exists (
				       select 1
				       from message_user_deletions mud
				       where mud.message_id = m.id and mud.user_id = cm.user_id
				   )
				where c.deleted_at is null
				  and c.id in (%s)
				group by c.id
				""".formatted(placeholders(conversationIds.size())),
				(RowCallbackHandler) rs -> result.put(
						rs.getObject("conversation_id", UUID.class),
						Math.toIntExact(rs.getLong("unread_count"))),
				args);
		return result;
	}

	public int countTotalUnread(UUID actorUserId, boolean includeArchived) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(m.id)
				from conversation_members cm
				join conversations c
				    on c.id = cm.conversation_id
				   and c.deleted_at is null
				left join messages last_read on last_read.id = cm.last_read_message_id
				left join messages m
				    on m.conversation_id = cm.conversation_id
				   and m.deleted_at is null
				   and m.is_recalled = false
				   and m.recalled_at is null
				   and m.sender_id <> cm.user_id
				   and m.created_at > coalesce(cm.last_read_at, last_read.created_at, timestamp '1970-01-01 00:00:00')
				   and not exists (
				       select 1
				       from message_user_deletions mud
				       where mud.message_id = m.id and mud.user_id = cm.user_id
				   )
				where cm.user_id = ?
				  and cm.left_at is null
				  and (? = true or cm.archived_at is null)
				""", Integer.class, actorUserId, includeArchived);
		return count == null ? 0 : count;
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
