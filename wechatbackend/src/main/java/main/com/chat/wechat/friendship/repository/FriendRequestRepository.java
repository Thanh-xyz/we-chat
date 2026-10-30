package main.com.chat.wechat.friendship.repository;

import main.com.chat.wechat.friendship.model.FriendRequest;
import main.com.chat.wechat.friendship.model.FriendRequestStatus;
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
public class FriendRequestRepository {
	private final JdbcTemplate jdbcTemplate;

	public FriendRequestRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public FriendRequest save(FriendRequest request) {
		jdbcTemplate.update("""
				insert into friend_requests (
				    id, requester_id, receiver_id, status, message,
				    responded_at, expires_at, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				request.id(),
				request.requesterId(),
				request.receiverId(),
				request.status().name(),
				request.message(),
				toTimestamp(request.respondedAt()),
				toTimestamp(request.expiresAt()),
				Timestamp.from(request.createdAt()),
				Timestamp.from(request.updatedAt()));
		return request;
	}

	public Optional<FriendRequest> findById(UUID id) {
		try {
			FriendRequest request = jdbcTemplate.queryForObject("""
					select *
					from friend_requests
					where id = ?
					""", rowMapper(), id);
			return Optional.ofNullable(request);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public List<FriendRequest> findIncoming(UUID receiverId, int limit, int offset) {
		return jdbcTemplate.query("""
				select fr.*
				from friend_requests fr
				where fr.receiver_id = ?
				  and fr.status = 'PENDING'
				  and (fr.expires_at is null or fr.expires_at > current_timestamp)
				  and not exists (
				      select 1 from friendships f
				      where f.status = 'ACTIVE'
				        and ((f.user_id = fr.receiver_id and f.friend_id = fr.requester_id)
				             or (f.user_id = fr.requester_id and f.friend_id = fr.receiver_id))
				  )
				order by fr.created_at desc
				limit ? offset ?
				""", rowMapper(), receiverId, limit, offset);
	}

	public List<FriendRequest> findOutgoing(UUID requesterId, int limit, int offset) {
		return jdbcTemplate.query("""
				select fr.*
				from friend_requests fr
				where fr.requester_id = ?
				  and fr.status = 'PENDING'
				  and (fr.expires_at is null or fr.expires_at > current_timestamp)
				  and not exists (
				      select 1 from friendships f
				      where f.status = 'ACTIVE'
				        and ((f.user_id = fr.requester_id and f.friend_id = fr.receiver_id)
				             or (f.user_id = fr.receiver_id and f.friend_id = fr.requester_id))
				  )
				order by fr.created_at desc
				limit ? offset ?
				""", rowMapper(), requesterId, limit, offset);
	}

	public Optional<FriendRequest> findPendingBetween(UUID firstUserId, UUID secondUserId) {
		List<FriendRequest> rows = jdbcTemplate.query("""
				select *
				from friend_requests
				where status = 'PENDING'
				  and (expires_at is null or expires_at > current_timestamp)
				  and ((requester_id = ? and receiver_id = ?) or (requester_id = ? and receiver_id = ?))
				order by created_at desc
				limit 1
				""", rowMapper(), firstUserId, secondUserId, secondUserId, firstUserId);
		return rows.stream().findFirst();
	}

	public boolean existsPending(UUID firstUserId, UUID secondUserId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from friend_requests
				where status = 'PENDING'
				  and (expires_at is null or expires_at > current_timestamp)
				  and ((requester_id = ? and receiver_id = ?) or (requester_id = ? and receiver_id = ?))
				""", Integer.class, firstUserId, secondUserId, secondUserId, firstUserId);
		return count != null && count > 0;
	}

	public FriendRequest updateStatus(UUID id, FriendRequestStatus status, Instant respondedAt, Instant updatedAt) {
		jdbcTemplate.update("""
				update friend_requests
				set status = ?, responded_at = ?, updated_at = ?
				where id = ?
				""", status.name(), toTimestamp(respondedAt), Timestamp.from(updatedAt), id);
		return findById(id).orElseThrow();
	}

	public int cancelPendingBetween(UUID firstUserId, UUID secondUserId, Instant updatedAt) {
		return jdbcTemplate.update("""
				update friend_requests
				set status = 'CANCELLED', responded_at = ?, updated_at = ?
				where status = 'PENDING'
				  and ((requester_id = ? and receiver_id = ?) or (requester_id = ? and receiver_id = ?))
				""", Timestamp.from(updatedAt), Timestamp.from(updatedAt), firstUserId, secondUserId, secondUserId, firstUserId);
	}

	public int expirePendingBetween(UUID firstUserId, UUID secondUserId, Instant expiredAt) {
		return jdbcTemplate.update("""
				update friend_requests
				set status = 'EXPIRED', responded_at = ?, updated_at = ?
				where status = 'PENDING'
				  and expires_at is not null
				  and expires_at <= ?
				  and ((requester_id = ? and receiver_id = ?) or (requester_id = ? and receiver_id = ?))
				""", Timestamp.from(expiredAt), Timestamp.from(expiredAt), Timestamp.from(expiredAt),
				firstUserId, secondUserId, secondUserId, firstUserId);
	}

	public long countIncomingPending(UUID receiverId) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*)
				from friend_requests fr
				where fr.receiver_id = ?
				  and fr.status = 'PENDING'
				  and (fr.expires_at is null or fr.expires_at > current_timestamp)
				  and not exists (
				      select 1 from friendships f
				      where f.status = 'ACTIVE'
				        and ((f.user_id = fr.receiver_id and f.friend_id = fr.requester_id)
				             or (f.user_id = fr.requester_id and f.friend_id = fr.receiver_id))
				  )
				""", Long.class, receiverId);
		return count == null ? 0 : count;
	}

	public long countOutgoingPending(UUID requesterId) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*)
				from friend_requests fr
				where fr.requester_id = ?
				  and fr.status = 'PENDING'
				  and (fr.expires_at is null or fr.expires_at > current_timestamp)
				  and not exists (
				      select 1 from friendships f
				      where f.status = 'ACTIVE'
				        and ((f.user_id = fr.requester_id and f.friend_id = fr.receiver_id)
				             or (f.user_id = fr.receiver_id and f.friend_id = fr.requester_id))
				  )
				""", Long.class, requesterId);
		return count == null ? 0 : count;
	}

	private RowMapper<FriendRequest> rowMapper() {
		return (rs, rowNum) -> mapRequest(rs);
	}

	private FriendRequest mapRequest(ResultSet rs) throws SQLException {
		return new FriendRequest(
				rs.getObject("id", UUID.class),
				rs.getObject("requester_id", UUID.class),
				rs.getObject("receiver_id", UUID.class),
				FriendRequestStatus.valueOf(rs.getString("status")),
				rs.getString("message"),
				toInstant(rs, "responded_at"),
				toInstant(rs, "expires_at"),
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
