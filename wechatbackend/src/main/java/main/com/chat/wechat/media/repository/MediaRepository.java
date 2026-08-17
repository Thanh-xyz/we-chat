package main.com.chat.wechat.media.repository;

import main.com.chat.wechat.media.model.MediaCategory;
import main.com.chat.wechat.media.model.MediaItem;
import main.com.chat.wechat.media.model.MediaStatistics;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MediaRepository {
	private final JdbcTemplate jdbcTemplate;

	public MediaRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<MediaItem> list(
			UUID conversationId,
			UUID actorUserId,
			MediaCategory category,
			UUID uploaderId,
			Instant from,
			Instant to,
			int limit,
			int offset) {
		Query query = visibleConversationQuery(conversationId, actorUserId, false);
		addFilters(query, category, uploaderId, from, to);
		query.sql.append(" order by ma.created_at desc, ma.id desc limit ? offset ?");
		query.args.add(limit);
		query.args.add(offset);
		return jdbcTemplate.query(query.sql.toString(), rowMapper(), query.args.toArray());
	}

	public List<MediaItem> search(
			UUID conversationId,
			UUID actorUserId,
			String fileName,
			MediaCategory category,
			UUID uploaderId,
			Instant from,
			Instant to,
			int limit,
			int offset) {
		Query query = visibleConversationQuery(conversationId, actorUserId, false);
		addFilters(query, category, uploaderId, from, to);
		if (StringUtils.hasText(fileName)) {
			String like = "%" + fileName.trim().toLowerCase() + "%";
			query.sql.append("""
					  and (
					      lower(coalesce(ma.original_file_name, ma.file_name, '')) like ?
					      or lower(coalesce(ma.mime_type, '')) like ?
					  )
					""");
			query.args.add(like);
			query.args.add(like);
		}
		query.sql.append(" order by ma.created_at desc, ma.id desc limit ? offset ?");
		query.args.add(limit);
		query.args.add(offset);
		return jdbcTemplate.query(query.sql.toString(), rowMapper(), query.args.toArray());
	}

	public Optional<MediaItem> findAccessibleById(UUID attachmentId, UUID actorUserId) {
		try {
			MediaItem item = jdbcTemplate.queryForObject("""
					select %s
					from message_attachments ma
					join conversation_members cm
					  on cm.conversation_id = ma.conversation_id
					 and cm.user_id = ?
					 and cm.left_at is null
					join conversations c
					  on c.id = ma.conversation_id
					 and c.deleted_at is null
					left join messages m
					  on m.id = ma.message_id
					left join users uploader
					  on uploader.id = ma.uploader_id
					where ma.id = ?
					  and ma.deleted_at is null
					  and (
					      ma.message_id is null
					      or (
					          m.deleted_at is null
					          and m.is_recalled = false
					          and m.recalled_at is null
					          and not exists (
					              select 1
					              from message_user_deletions mud
					              where mud.message_id = m.id and mud.user_id = ?
					          )
					      )
					  )
					""".formatted(selectColumns()), rowMapper(), actorUserId, attachmentId, actorUserId);
			return Optional.ofNullable(item);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public void incrementDownloadCount(UUID attachmentId) {
		jdbcTemplate.update("""
				update message_attachments
				set download_count = coalesce(download_count, 0) + 1,
				    updated_at = ?
				where id = ? and deleted_at is null
				""", Timestamp.from(Instant.now()), attachmentId);
	}

	public int softDelete(UUID attachmentId, Instant deletedAt) {
		return jdbcTemplate.update("""
				update message_attachments
				set deleted_at = ?, updated_at = ?
				where id = ? and deleted_at is null
				""", Timestamp.from(deletedAt), Timestamp.from(deletedAt), attachmentId);
	}

	public MediaStatistics statistics(UUID conversationId, UUID actorUserId) {
		Query query = visibleConversationQuery(conversationId, actorUserId, false);
		MediaTotals totals = jdbcTemplate.queryForObject("""
				select count(*) as total_files,
				       count(*) filter (where coalesce(ma.file_category, 'FILE') = 'IMAGE') as total_images,
				       count(*) filter (where coalesce(ma.file_category, 'FILE') = 'VIDEO') as total_videos,
				       count(*) filter (where coalesce(ma.file_category, 'FILE') = 'VOICE') as total_voices,
				       count(*) filter (where coalesce(ma.file_category, 'FILE') = 'FILE') as total_regular_files,
				       coalesce(sum(coalesce(ma.file_size, 0)), 0) as total_storage_bytes
				from message_attachments ma
				join conversation_members cm
				  on cm.conversation_id = ma.conversation_id
				 and cm.user_id = ?
				 and cm.left_at is null
				join conversations c
				  on c.id = ma.conversation_id
				 and c.deleted_at is null
				join messages m
				  on m.id = ma.message_id
				where ma.conversation_id = ?
				  and ma.deleted_at is null
				  and ma.message_id is not null
				  and m.deleted_at is null
				  and m.is_recalled = false
				  and m.recalled_at is null
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = ?
				  )
				""", (rs, rowNum) -> new MediaTotals(
				rs.getLong("total_files"),
				rs.getLong("total_images"),
				rs.getLong("total_videos"),
				rs.getLong("total_voices"),
				rs.getLong("total_regular_files"),
				rs.getLong("total_storage_bytes")), actorUserId, conversationId, actorUserId);
		MediaItem largestFile = firstByOrder(query, "ma.file_size desc nulls last, ma.created_at desc, ma.id desc");
		MediaItem latestUpload = firstByOrder(query, "ma.created_at desc, ma.id desc");
		return new MediaStatistics(
				totals.totalFiles(),
				totals.totalImages(),
				totals.totalVideos(),
				totals.totalVoices(),
				totals.totalRegularFiles(),
				totals.totalStorageBytes(),
				largestFile,
				latestUpload);
	}

	private MediaItem firstByOrder(Query baseQuery, String orderBy) {
		Query query = new Query(new StringBuilder(baseQuery.sql), new ArrayList<>(baseQuery.args));
		query.sql.append(" order by ").append(orderBy).append(" limit 1");
		List<MediaItem> items = jdbcTemplate.query(query.sql.toString(), rowMapper(), query.args.toArray());
		return items.isEmpty() ? null : items.getFirst();
	}

	private Query visibleConversationQuery(UUID conversationId, UUID actorUserId, boolean includePending) {
		Query query = new Query(new StringBuilder("""
				select %s
				from message_attachments ma
				join conversation_members cm
				  on cm.conversation_id = ma.conversation_id
				 and cm.user_id = ?
				 and cm.left_at is null
				join conversations c
				  on c.id = ma.conversation_id
				 and c.deleted_at is null
				join messages m
				  on m.id = ma.message_id
				left join users uploader
				  on uploader.id = ma.uploader_id
				where ma.conversation_id = ?
				  and ma.deleted_at is null
				  and m.deleted_at is null
				  and m.is_recalled = false
				  and m.recalled_at is null
				  and not exists (
				      select 1
				      from message_user_deletions mud
				      where mud.message_id = m.id and mud.user_id = ?
				  )
				""".formatted(selectColumns())), new ArrayList<>());
		query.args.add(actorUserId);
		query.args.add(conversationId);
		query.args.add(actorUserId);
		if (!includePending) {
			query.sql.append("  and ma.message_id is not null\n");
		}
		return query;
	}

	private void addFilters(Query query, MediaCategory category, UUID uploaderId, Instant from, Instant to) {
		if (category != null) {
			query.sql.append("  and ma.file_category = ?\n");
			query.args.add(category.name());
		}
		if (uploaderId != null) {
			query.sql.append("  and ma.uploader_id = ?\n");
			query.args.add(uploaderId);
		}
		if (from != null) {
			query.sql.append("  and ma.created_at >= ?\n");
			query.args.add(Timestamp.from(from));
		}
		if (to != null) {
			query.sql.append("  and ma.created_at <= ?\n");
			query.args.add(Timestamp.from(to));
		}
	}

	private String selectColumns() {
		return """
				ma.id,
				ma.message_id,
				ma.conversation_id,
				ma.uploader_id,
				uploader.username as uploader_username,
				uploader.display_name as uploader_display_name,
				uploader.avatar_url as uploader_avatar_url,
				coalesce(ma.original_file_name, ma.file_name) as file_name,
				ma.storage_key,
				ma.file_url,
				ma.mime_type,
				coalesce(ma.file_category, 'FILE') as file_category,
				ma.file_size,
				ma.width,
				ma.height,
				ma.duration_seconds,
				ma.checksum,
				ma.scan_status,
				coalesce(ma.download_count, 0) as download_count,
				ma.deleted_at,
				ma.created_at,
				ma.updated_at
				""";
	}

	private RowMapper<MediaItem> rowMapper() {
		return (rs, rowNum) -> mapItem(rs);
	}

	private MediaItem mapItem(ResultSet rs) throws SQLException {
		return new MediaItem(
				rs.getObject("id", UUID.class),
				rs.getObject("message_id", UUID.class),
				rs.getObject("conversation_id", UUID.class),
				rs.getObject("uploader_id", UUID.class),
				rs.getString("uploader_username"),
				rs.getString("uploader_display_name"),
				rs.getString("uploader_avatar_url"),
				rs.getString("file_name"),
				rs.getString("storage_key"),
				rs.getString("file_url"),
				rs.getString("mime_type"),
				MediaCategory.from(rs.getString("file_category")),
				readLong(rs, "file_size"),
				readInteger(rs, "width"),
				readInteger(rs, "height"),
				readInteger(rs, "duration_seconds"),
				rs.getString("checksum"),
				rs.getString("scan_status"),
				readLong(rs, "download_count"),
				toInstant(rs, "deleted_at"),
				toInstant(rs, "created_at"),
				toInstant(rs, "updated_at"));
	}

	private Long readLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private Integer readInteger(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private record Query(StringBuilder sql, List<Object> args) {
	}

	private record MediaTotals(
			long totalFiles,
			long totalImages,
			long totalVideos,
			long totalVoices,
			long totalRegularFiles,
			long totalStorageBytes) {
	}
}
