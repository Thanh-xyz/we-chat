package main.com.chat.wechat.message.repository;

import main.com.chat.wechat.message.model.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
				    edited_at, deleted_at, recalled_at, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
				Timestamp.from(message.createdAt()),
				Timestamp.from(message.updatedAt()));
		return message;
	}

	public boolean existsInConversation(UUID messageId, UUID conversationId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from messages
				where id = ? and conversation_id = ? and deleted_at is null and recalled_at is null
				""", Integer.class, messageId, conversationId);
		return count != null && count > 0;
	}

	public List<Message> findByConversationId(UUID conversationId, int limit, int offset) {
		return jdbcTemplate.query("""
				select *
				from messages
				where conversation_id = ? and deleted_at is null
				order by created_at desc
				limit ? offset ?
				""", rowMapper(), conversationId, limit, offset);
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
