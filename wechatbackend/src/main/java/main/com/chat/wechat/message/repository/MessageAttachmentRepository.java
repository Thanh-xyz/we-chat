package main.com.chat.wechat.message.repository;

import main.com.chat.wechat.message.model.MessageAttachment;
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
import java.util.UUID;

@Repository
public class MessageAttachmentRepository {
	private final JdbcTemplate jdbcTemplate;

	public MessageAttachmentRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public MessageAttachment save(MessageAttachment attachment) {
		jdbcTemplate.update("""
				insert into message_attachments (
				    id, message_id, storage_key, file_url, file_name,
				    mime_type, file_type, file_size, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				attachment.id(),
				attachment.messageId(),
				attachment.fileUrl(),
				attachment.fileUrl(),
				attachment.fileName(),
				attachment.fileType(),
				attachment.fileType(),
				attachment.fileSize(),
				Timestamp.from(attachment.createdAt()));
		return attachment;
	}

	public List<MessageAttachment> findByMessageId(UUID messageId) {
		return jdbcTemplate.query("""
				select *
				from message_attachments
				where message_id = ?
				order by created_at, id
				""", rowMapper(), messageId);
	}

	public Map<UUID, List<MessageAttachment>> findByMessageIds(List<UUID> messageIds) {
		if (messageIds == null || messageIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, List<MessageAttachment>> result = new LinkedHashMap<>();
		for (UUID messageId : messageIds) {
			result.put(messageId, new ArrayList<>());
		}
		jdbcTemplate.query("""
				select *
				from message_attachments
				where message_id in (%s)
				order by message_id, created_at, id
				""".formatted(placeholders(messageIds.size())),
				(RowCallbackHandler) rs -> {
					MessageAttachment attachment = mapAttachment(rs);
					result.computeIfAbsent(attachment.messageId(), key -> new ArrayList<>()).add(attachment);
				},
				messageIds.toArray());
		return result;
	}

	private RowMapper<MessageAttachment> rowMapper() {
		return (rs, rowNum) -> mapAttachment(rs);
	}

	private MessageAttachment mapAttachment(ResultSet rs) throws SQLException {
		return new MessageAttachment(
				rs.getObject("id", UUID.class),
				rs.getObject("message_id", UUID.class),
				rs.getString("file_name"),
				rs.getString("file_url"),
				firstNonBlank(rs.getString("file_type"), rs.getString("mime_type")),
				readLong(rs, "file_size"),
				toInstant(rs, "created_at"));
	}

	private Long readLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private String firstNonBlank(String first, String second) {
		return first != null && !first.isBlank() ? first : second;
	}

	private String placeholders(int count) {
		return String.join(",", Collections.nCopies(count, "?"));
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
