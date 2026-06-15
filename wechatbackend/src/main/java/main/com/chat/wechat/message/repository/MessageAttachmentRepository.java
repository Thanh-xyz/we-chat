package main.com.chat.wechat.message.repository;

import main.com.chat.wechat.message.model.MessageAttachment;
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
public class MessageAttachmentRepository {
	private final JdbcTemplate jdbcTemplate;

	public MessageAttachmentRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public MessageAttachment save(MessageAttachment attachment) {
		jdbcTemplate.update("""
				insert into message_attachments (
				    id, message_id, uploader_id, conversation_id, original_file_name, storage_key,
				    file_url, file_name, mime_type, file_type, file_size, checksum, scan_status,
				    deleted_at, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				attachment.id(),
				attachment.messageId(),
				attachment.uploaderId(),
				attachment.conversationId(),
				attachment.originalFileName(),
				attachment.storageKey(),
				attachment.fileUrl(),
				attachment.originalFileName(),
				attachment.mimeType(),
				attachment.fileType(),
				attachment.fileSize(),
				attachment.checksum(),
				attachment.scanStatus(),
				toTimestamp(attachment.deletedAt()),
				Timestamp.from(attachment.createdAt()),
				Timestamp.from(attachment.updatedAt()));
		return attachment;
	}

	public Optional<MessageAttachment> findById(UUID id) {
		try {
			MessageAttachment attachment = jdbcTemplate.queryForObject("""
					select *
					from message_attachments
					where id = ? and deleted_at is null
					""", rowMapper(), id);
			return Optional.ofNullable(attachment);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Optional<MessageAttachment> findAccessibleById(UUID id, UUID userId) {
		try {
			MessageAttachment attachment = jdbcTemplate.queryForObject("""
					select ma.*
					from message_attachments ma
					join conversation_members cm
					  on cm.conversation_id = ma.conversation_id
					 and cm.user_id = ?
					 and cm.left_at is null
					where ma.id = ?
					  and ma.deleted_at is null
					""", rowMapper(), userId, id);
			return Optional.ofNullable(attachment);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public List<MessageAttachment> findByMessageId(UUID messageId) {
		return jdbcTemplate.query("""
				select *
				from message_attachments
				where message_id = ? and deleted_at is null
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
				where message_id in (%s) and deleted_at is null
				order by message_id, created_at, id
				""".formatted(placeholders(messageIds.size())),
				(RowCallbackHandler) rs -> {
					MessageAttachment attachment = mapAttachment(rs);
					result.computeIfAbsent(attachment.messageId(), key -> new ArrayList<>()).add(attachment);
				},
				messageIds.toArray());
		return result;
	}

	public List<MessageAttachment> findPendingByUploaderAndConversation(
			UUID uploaderId,
			UUID conversationId,
			List<UUID> attachmentIds) {
		if (attachmentIds == null || attachmentIds.isEmpty()) {
			return List.of();
		}
		return jdbcTemplate.query("""
				select *
				from message_attachments
				where id in (%s)
				  and uploader_id = ?
				  and conversation_id = ?
				  and message_id is null
				  and deleted_at is null
				order by created_at, id
				""".formatted(placeholders(attachmentIds.size())),
				rowMapper(),
				argsWithAttachmentIds(attachmentIds, uploaderId, conversationId));
	}

	public MessageAttachment attachToMessage(UUID attachmentId, UUID messageId, Instant updatedAt) {
		jdbcTemplate.update("""
				update message_attachments
				set message_id = ?, updated_at = ?
				where id = ? and deleted_at is null
				""", messageId, Timestamp.from(updatedAt), attachmentId);
		return findById(attachmentId).orElseThrow();
	}

	public void softDelete(UUID attachmentId, Instant deletedAt) {
		jdbcTemplate.update("""
				update message_attachments
				set deleted_at = ?, updated_at = ?
				where id = ? and deleted_at is null
				""", Timestamp.from(deletedAt), Timestamp.from(deletedAt), attachmentId);
	}

	public void softDeleteByMessageId(UUID messageId, Instant deletedAt) {
		jdbcTemplate.update("""
				update message_attachments
				set deleted_at = ?, updated_at = ?
				where message_id = ? and deleted_at is null
				""", Timestamp.from(deletedAt), Timestamp.from(deletedAt), messageId);
	}

	private RowMapper<MessageAttachment> rowMapper() {
		return (rs, rowNum) -> mapAttachment(rs);
	}

	private MessageAttachment mapAttachment(ResultSet rs) throws SQLException {
		return new MessageAttachment(
				rs.getObject("id", UUID.class),
				rs.getObject("message_id", UUID.class),
				rs.getObject("uploader_id", UUID.class),
				rs.getObject("conversation_id", UUID.class),
				firstNonBlank(rs.getString("original_file_name"), rs.getString("file_name")),
				rs.getString("storage_key"),
				rs.getString("file_url"),
				rs.getString("mime_type"),
				firstNonBlank(rs.getString("file_type"), rs.getString("mime_type")),
				readLong(rs, "file_size"),
				rs.getString("checksum"),
				rs.getString("scan_status"),
				toInstant(rs, "deleted_at"),
				toInstant(rs, "created_at"),
				toInstant(rs, "updated_at"));
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

	private Object[] argsWithAttachmentIds(List<UUID> attachmentIds, UUID uploaderId, UUID conversationId) {
		Object[] args = new Object[attachmentIds.size() + 2];
		for (int i = 0; i < attachmentIds.size(); i++) {
			args[i] = attachmentIds.get(i);
		}
		args[attachmentIds.size()] = uploaderId;
		args[attachmentIds.size() + 1] = conversationId;
		return args;
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}
}
