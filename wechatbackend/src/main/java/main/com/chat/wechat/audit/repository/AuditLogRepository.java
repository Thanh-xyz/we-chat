package main.com.chat.wechat.audit.repository;

import main.com.chat.wechat.audit.dto.AuditLogExportRequest;
import main.com.chat.wechat.audit.dto.AuditLogSearchRequest;
import main.com.chat.wechat.audit.model.AuditLog;
import main.com.chat.wechat.audit.model.AuditResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuditLogRepository {
	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public AuditLogRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
	}

	public AuditLog save(AuditLog auditLog) {
		jdbcTemplate.update("""
				insert into audit_logs (
				    id, actor_user_id, actor_username, actor_email, action,
				    target_type, target_id, resource_type, resource_id,
				    target_user_id, conversation_id, message_id,
				    ip_address, user_agent, request_id, trace_id,
				    before_value, after_value, metadata, result, failure_reason, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				auditLog.id(),
				auditLog.actorUserId(),
				auditLog.actorUsername(),
				auditLog.actorEmail(),
				auditLog.action(),
				auditLog.resourceType(),
				auditLog.resourceId(),
				auditLog.resourceType(),
				auditLog.resourceId(),
				auditLog.targetUserId(),
				auditLog.conversationId(),
				auditLog.messageId(),
				auditLog.ipAddress(),
				auditLog.userAgent(),
				auditLog.requestId(),
				auditLog.traceId(),
				jsonParameter(auditLog.beforeValue()),
				jsonParameter(auditLog.afterValue()),
				jsonParameter(auditLog.metadata()),
				auditLog.result().name(),
				auditLog.failureReason(),
				Timestamp.from(auditLog.createdAt()));
		return auditLog;
	}

	public List<AuditLog> findRecent(int limit, int offset) {
		return search(new AuditLogSearchRequest(null, null, null, null, null, null, null, null, null, null, limit, offset));
	}

	public Optional<AuditLog> findById(UUID id) {
		List<AuditLog> rows = jdbcTemplate.query("""
				select *
				from audit_logs
				where id = ?
				""", rowMapper(), id);
		return rows.stream().findFirst();
	}

	public List<AuditLog> search(AuditLogSearchRequest request) {
		Query query = buildSearchQuery(request, true);
		return namedParameterJdbcTemplate.query(query.sql(), query.parameters(), rowMapper());
	}

	public long count(AuditLogSearchRequest request) {
		Query query = buildSearchQuery(request, false);
		Long count = namedParameterJdbcTemplate.queryForObject(query.sql(), query.parameters(), Long.class);
		return count == null ? 0 : count;
	}

	public List<AuditLog> export(AuditLogExportRequest request) {
		AuditLogSearchRequest searchRequest = new AuditLogSearchRequest(
				request.action(),
				request.actorUserId(),
				request.targetUserId(),
				request.conversationId(),
				request.messageId(),
				request.resourceType(),
				request.resourceId(),
				request.result(),
				request.from(),
				request.to(),
				request.limit(),
				0);
		return search(searchRequest);
	}

	private Query buildSearchQuery(AuditLogSearchRequest request, boolean paged) {
		StringBuilder sql = new StringBuilder(paged ? "select * from audit_logs where 1 = 1" : "select count(*) from audit_logs where 1 = 1");
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		if (hasText(request.action())) {
			sql.append(" and action = :action");
			parameters.addValue("action", request.action().trim().toUpperCase());
		}
		if (request.actorUserId() != null) {
			sql.append(" and actor_user_id = :actorUserId");
			parameters.addValue("actorUserId", request.actorUserId());
		}
		if (request.targetUserId() != null) {
			sql.append(" and target_user_id = :targetUserId");
			parameters.addValue("targetUserId", request.targetUserId());
		}
		if (request.conversationId() != null) {
			sql.append(" and conversation_id = :conversationId");
			parameters.addValue("conversationId", request.conversationId());
		}
		if (request.messageId() != null) {
			sql.append(" and message_id = :messageId");
			parameters.addValue("messageId", request.messageId());
		}
		if (hasText(request.resourceType())) {
			sql.append(" and resource_type = :resourceType");
			parameters.addValue("resourceType", request.resourceType().trim().toUpperCase());
		}
		if (hasText(request.resourceId())) {
			sql.append(" and resource_id = :resourceId");
			parameters.addValue("resourceId", request.resourceId().trim());
		}
		if (request.result() != null) {
			sql.append(" and result = :result");
			parameters.addValue("result", request.result().name());
		}
		if (request.from() != null) {
			sql.append(" and created_at >= :from");
			parameters.addValue("from", Timestamp.from(request.from()));
		}
		if (request.to() != null) {
			sql.append(" and created_at <= :to");
			parameters.addValue("to", Timestamp.from(request.to()));
		}
		if (paged) {
			sql.append(" order by created_at desc limit :limit offset :offset");
			parameters.addValue("limit", request.limit());
			parameters.addValue("offset", request.offset());
		}
		return new Query(sql.toString(), parameters);
	}

	private RowMapper<AuditLog> rowMapper() {
		return (rs, rowNum) -> mapAuditLog(rs);
	}

	private AuditLog mapAuditLog(ResultSet rs) throws SQLException {
		return new AuditLog(
				rs.getObject("id", UUID.class),
				rs.getObject("actor_user_id", UUID.class),
				rs.getString("actor_username"),
				rs.getString("actor_email"),
				rs.getString("action"),
				rs.getString("resource_type"),
				rs.getString("resource_id"),
				rs.getObject("target_user_id", UUID.class),
				rs.getObject("conversation_id", UUID.class),
				rs.getObject("message_id", UUID.class),
				rs.getString("request_id"),
				rs.getString("trace_id"),
				rs.getString("before_value"),
				rs.getString("after_value"),
				rs.getString("metadata"),
				rs.getString("ip_address"),
				rs.getString("user_agent"),
				toResult(rs.getString("result")),
				rs.getString("failure_reason"),
				toInstant(rs, "created_at"));
	}

	private AuditResult toResult(String value) {
		if (!hasText(value)) {
			return AuditResult.SUCCESS;
		}
		try {
			return AuditResult.valueOf(value);
		} catch (IllegalArgumentException exception) {
			return AuditResult.SUCCESS;
		}
	}

	private SqlParameterValue jsonParameter(String value) {
		return new SqlParameterValue(Types.OTHER, value);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private record Query(String sql, MapSqlParameterSource parameters) {
	}
}
