package main.com.chat.wechat.conversation.repository;

import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.model.DirectConversationPair;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationRepository {
	private final JdbcTemplate jdbcTemplate;

	public ConversationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Conversation save(Conversation conversation) {
		jdbcTemplate.update("""
				insert into conversations (
				    id, type, name, avatar_url, created_by, last_message_id,
				    last_message_at, deleted_at, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				conversation.id(),
				conversation.type(),
				conversation.name(),
				conversation.avatarUrl(),
				conversation.createdBy(),
				conversation.lastMessageId(),
				toTimestamp(conversation.lastMessageAt()),
				toTimestamp(conversation.deletedAt()),
				Timestamp.from(conversation.createdAt()),
				Timestamp.from(conversation.updatedAt()));
		return conversation;
	}

	public Optional<Conversation> findById(UUID id) {
		try {
			Conversation conversation = jdbcTemplate.queryForObject("""
					select *
					from conversations
					where id = ? and deleted_at is null
					""", rowMapper(), id);
			return Optional.ofNullable(conversation);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public List<Conversation> findByMember(UUID userId, boolean includeArchived, int limit, int offset) {
		return jdbcTemplate.query("""
				select c.*
				from conversations c
				join conversation_members cm on cm.conversation_id = c.id
				where cm.user_id = ?
				  and cm.left_at is null
				  and c.deleted_at is null
				  and (? = true or cm.archived_at is null)
				order by cm.pinned_at desc nulls last, c.last_message_at desc nulls last, c.updated_at desc
				limit ? offset ?
				""", rowMapper(), userId, includeArchived, limit, offset);
	}

	public List<Conversation> searchByMember(UUID userId, String query, boolean includeArchived, int limit, int offset) {
		if (query == null || query.isBlank()) {
			return Collections.emptyList();
		}
		String normalizedQuery = "%" + query.trim().toLowerCase() + "%";
		return jdbcTemplate.query("""
				select id, type, name, avatar_url, created_by, last_message_id,
				       last_message_at, deleted_at, created_at, updated_at
				from (
				    select distinct on (c.id)
				           c.*,
				           actor_member.pinned_at as actor_pinned_at
				    from conversations c
				    join conversation_members actor_member
				        on actor_member.conversation_id = c.id
				       and actor_member.user_id = ?
				       and actor_member.left_at is null
				    left join conversation_members cm
				        on cm.conversation_id = c.id
				       and cm.left_at is null
				    left join users u on u.id = cm.user_id
				    where c.deleted_at is null
				      and (? = true or actor_member.archived_at is null)
				      and (
				          (c.type = 'GROUP' and lower(coalesce(c.name, '')) like ?)
				          or lower(coalesce(u.username, '')) like ?
				          or lower(coalesce(u.email, '')) like ?
				          or lower(coalesce(u.display_name, '')) like ?
				      )
				    order by c.id, actor_member.pinned_at desc nulls last,
				             c.last_message_at desc nulls last, c.updated_at desc
				) search_result
				order by actor_pinned_at desc nulls last,
				         last_message_at desc nulls last, updated_at desc
				limit ? offset ?
				""", rowMapper(), userId, includeArchived, normalizedQuery, normalizedQuery, normalizedQuery, normalizedQuery, limit, offset);
	}

	public Optional<Conversation> findDirectByPair(UUID userLowId, UUID userHighId) {
		DirectConversationPair pair = DirectConversationPair.of(userLowId, userHighId);
		try {
			Conversation conversation = jdbcTemplate.queryForObject("""
					select c.*
					from direct_conversations dc
					join conversations c on c.id = dc.conversation_id
					where dc.user_low_id = ? and dc.user_high_id = ? and c.deleted_at is null
					""", rowMapper(), pair.userLowId(), pair.userHighId());
			return Optional.ofNullable(conversation);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public boolean saveDirectConversation(UUID conversationId, UUID userLowId, UUID userHighId) {
		DirectConversationPair pair = DirectConversationPair.of(userLowId, userHighId);
		int insertedRows = jdbcTemplate.update("""
				insert into direct_conversations (conversation_id, user_low_id, user_high_id)
				values (?, ?, ?)
				on conflict (user_low_id, user_high_id) do nothing
				""", conversationId, pair.userLowId(), pair.userHighId());
		return insertedRows == 1;
	}

	public void deleteById(UUID id) {
		jdbcTemplate.update("""
				delete from conversations
				where id = ? and last_message_id is null
				""", id);
	}

	public void updateLastMessage(UUID conversationId, UUID messageId, Instant lastMessageAt) {
		jdbcTemplate.update("""
				update conversations
				set last_message_id = ?, last_message_at = ?, updated_at = ?
				where id = ? and deleted_at is null
				""", messageId, Timestamp.from(lastMessageAt), Timestamp.from(lastMessageAt), conversationId);
	}

	public Optional<Conversation> updateGroup(UUID conversationId, String name, String avatarUrl, Instant updatedAt) {
		try {
			Conversation conversation = jdbcTemplate.queryForObject("""
					update conversations
					set name = coalesce(?, name),
					    avatar_url = coalesce(?, avatar_url),
					    updated_at = ?
					where id = ? and type = 'GROUP' and deleted_at is null
					returning *
					""", rowMapper(), name, avatarUrl, Timestamp.from(updatedAt), conversationId);
			return Optional.ofNullable(conversation);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	private RowMapper<Conversation> rowMapper() {
		return (rs, rowNum) -> mapConversation(rs);
	}

	private Conversation mapConversation(ResultSet rs) throws SQLException {
		return new Conversation(
				rs.getObject("id", UUID.class),
				rs.getString("type"),
				rs.getString("name"),
				rs.getString("avatar_url"),
				rs.getObject("created_by", UUID.class),
				rs.getObject("last_message_id", UUID.class),
				toInstant(rs, "last_message_at"),
				toInstant(rs, "deleted_at"),
				toInstant(rs, "created_at"),
				toInstant(rs, "updated_at"));
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
