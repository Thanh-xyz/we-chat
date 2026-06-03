package main.com.chat.wechat.conversation.repository;

import main.com.chat.wechat.conversation.model.ConversationMember;
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
public class ConversationMemberRepository {
	private final JdbcTemplate jdbcTemplate;

	public ConversationMemberRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public ConversationMember save(ConversationMember member) {
		jdbcTemplate.update("""
				insert into conversation_members (
				    conversation_id, user_id, member_role, nickname, joined_at, left_at,
				    muted_until, pinned_at, last_read_message_id, muted
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (conversation_id, user_id) do nothing
				""",
				member.conversationId(),
				member.userId(),
				member.memberRole(),
				member.nickname(),
				Timestamp.from(member.joinedAt()),
				toTimestamp(member.leftAt()),
				toTimestamp(member.mutedUntil()),
				toTimestamp(member.pinnedAt()),
				member.lastReadMessageId(),
				member.muted());
		return member;
	}

	public boolean isMember(UUID conversationId, UUID userId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from conversation_members
				where conversation_id = ? and user_id = ? and left_at is null
				""", Integer.class, conversationId, userId);
		return count != null && count > 0;
	}

	public List<UUID> findMemberIds(UUID conversationId) {
		return jdbcTemplate.queryForList("""
				select user_id
				from conversation_members
				where conversation_id = ? and left_at is null
				order by joined_at
				""", UUID.class, conversationId);
	}

	public List<ConversationMember> findByConversationId(UUID conversationId) {
		return jdbcTemplate.query("""
				select *
				from conversation_members
				where conversation_id = ? and left_at is null
				order by joined_at
				""", rowMapper(), conversationId);
	}

	private RowMapper<ConversationMember> rowMapper() {
		return (rs, rowNum) -> mapMember(rs);
	}

	private ConversationMember mapMember(ResultSet rs) throws SQLException {
		return new ConversationMember(
				rs.getObject("conversation_id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("member_role"),
				rs.getString("nickname"),
				toInstant(rs, "joined_at"),
				toInstant(rs, "left_at"),
				toInstant(rs, "muted_until"),
				toInstant(rs, "pinned_at"),
				rs.getObject("last_read_message_id", UUID.class),
				rs.getBoolean("muted"));
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
