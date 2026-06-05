package main.com.chat.wechat.conversation.repository;

import main.com.chat.wechat.conversation.model.ConversationMember;
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
public class ConversationMemberRepository {
	private final JdbcTemplate jdbcTemplate;

	public ConversationMemberRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public ConversationMember save(ConversationMember member) {
		jdbcTemplate.update("""
				insert into conversation_members (
				    conversation_id, user_id, member_role, nickname, joined_at, left_at,
				    muted_until, pinned_at, last_read_message_id, read_at, muted
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
				toTimestamp(member.readAt()),
				member.muted());
		return member;
	}

	public ConversationMember addOrReactivate(ConversationMember member) {
		jdbcTemplate.update("""
				insert into conversation_members (
				    conversation_id, user_id, member_role, nickname, joined_at, left_at,
				    muted_until, pinned_at, last_read_message_id, read_at, muted
				)
				values (?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?)
				on conflict (conversation_id, user_id)
				do update set
				    member_role = excluded.member_role,
				    joined_at = excluded.joined_at,
				    left_at = null,
				    muted = false,
				    muted_until = null
				where conversation_members.left_at is not null
				""",
				member.conversationId(),
				member.userId(),
				member.memberRole(),
				member.nickname(),
				Timestamp.from(member.joinedAt()),
				toTimestamp(member.mutedUntil()),
				toTimestamp(member.pinnedAt()),
				member.lastReadMessageId(),
				toTimestamp(member.readAt()),
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

	public Map<UUID, List<UUID>> findMemberIdsByConversationIds(List<UUID> conversationIds) {
		if (conversationIds == null || conversationIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, List<UUID>> result = new LinkedHashMap<>();
		for (UUID conversationId : conversationIds) {
			result.put(conversationId, new ArrayList<>());
		}
		jdbcTemplate.query("""
				select conversation_id, user_id
				from conversation_members
				where conversation_id in (%s) and left_at is null
				order by conversation_id, joined_at
				""".formatted(placeholders(conversationIds.size())),
				(RowCallbackHandler) rs -> {
					UUID conversationId = rs.getObject("conversation_id", UUID.class);
					UUID userId = rs.getObject("user_id", UUID.class);
					result.computeIfAbsent(conversationId, key -> new ArrayList<>()).add(userId);
				},
				conversationIds.toArray());
		return result;
	}

	public Optional<ConversationMember> findActiveMember(UUID conversationId, UUID userId) {
		try {
			ConversationMember member = jdbcTemplate.queryForObject("""
					select *
					from conversation_members
					where conversation_id = ? and user_id = ? and left_at is null
					""", rowMapper(), conversationId, userId);
			return Optional.ofNullable(member);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Map<UUID, ConversationMember> findActiveMembersByConversationIds(UUID userId, List<UUID> conversationIds) {
		if (conversationIds == null || conversationIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, ConversationMember> result = new LinkedHashMap<>();
		jdbcTemplate.query("""
				select *
				from conversation_members
				where user_id = ?
				  and conversation_id in (%s)
				  and left_at is null
				""".formatted(placeholders(conversationIds.size())),
				(RowCallbackHandler) rs -> {
					ConversationMember member = mapMember(rs);
					result.put(member.conversationId(), member);
				},
				argsWithUserId(userId, conversationIds));
		return result;
	}

	public List<ConversationMember> findByConversationId(UUID conversationId) {
		return jdbcTemplate.query("""
				select *
				from conversation_members
				where conversation_id = ? and left_at is null
				order by joined_at
				""", rowMapper(), conversationId);
	}

	public int activeMemberCount(UUID conversationId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from conversation_members
				where conversation_id = ? and left_at is null
				""", Integer.class, conversationId);
		return count == null ? 0 : count;
	}

	public int activeOwnerCount(UUID conversationId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from conversation_members
				where conversation_id = ? and member_role = 'OWNER' and left_at is null
				""", Integer.class, conversationId);
		return count == null ? 0 : count;
	}

	public void markLeft(UUID conversationId, UUID userId, Instant leftAt) {
		jdbcTemplate.update("""
				update conversation_members
				set left_at = ?
				where conversation_id = ? and user_id = ? and left_at is null
				""", Timestamp.from(leftAt), conversationId, userId);
	}

	public void updateRead(UUID conversationId, UUID userId, UUID lastReadMessageId, Instant readAt) {
		jdbcTemplate.update("""
				update conversation_members
				set last_read_message_id = ?, read_at = ?
				where conversation_id = ? and user_id = ? and left_at is null
				""", lastReadMessageId, Timestamp.from(readAt), conversationId, userId);
	}

	public ConversationMember updatePinnedAt(UUID conversationId, UUID userId, Instant pinnedAt) {
		jdbcTemplate.update("""
				update conversation_members
				set pinned_at = ?
				where conversation_id = ? and user_id = ? and left_at is null
				""", toTimestamp(pinnedAt), conversationId, userId);
		return findActiveMember(conversationId, userId).orElseThrow();
	}

	public ConversationMember updateMute(UUID conversationId, UUID userId, boolean muted, Instant mutedUntil) {
		jdbcTemplate.update("""
				update conversation_members
				set muted = ?, muted_until = ?
				where conversation_id = ? and user_id = ? and left_at is null
				""", muted, toTimestamp(mutedUntil), conversationId, userId);
		return findActiveMember(conversationId, userId).orElseThrow();
	}

	public ConversationMember updateArchivedAt(UUID conversationId, UUID userId, Instant archivedAt) {
		jdbcTemplate.update("""
				update conversation_members
				set archived_at = ?
				where conversation_id = ? and user_id = ? and left_at is null
				""", toTimestamp(archivedAt), conversationId, userId);
		return findActiveMember(conversationId, userId).orElseThrow();
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
				toInstant(rs, "archived_at"),
				rs.getObject("last_read_message_id", UUID.class),
				toInstant(rs, "read_at"),
				rs.getBoolean("muted"));
	}

	private String placeholders(int count) {
		return String.join(",", Collections.nCopies(count, "?"));
	}

	private Object[] argsWithUserId(UUID userId, List<UUID> conversationIds) {
		Object[] args = new Object[conversationIds.size() + 1];
		args[0] = userId;
		for (int i = 0; i < conversationIds.size(); i++) {
			args[i + 1] = conversationIds.get(i);
		}
		return args;
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
