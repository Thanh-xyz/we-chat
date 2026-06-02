package main.com.chat.wechat.audit.repository;

import main.com.chat.wechat.audit.model.AuditLog;
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
public class AuditLogRepository {
	private final JdbcTemplate jdbcTemplate;

	public AuditLogRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public AuditLog save(AuditLog auditLog) {
		jdbcTemplate.update("""
				insert into audit_logs (
				    id, actor_user_id, action, target_type, target_id,
				    before_value, after_value, ip_address, user_agent, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				auditLog.id(),
				auditLog.actorUserId(),
				auditLog.action(),
				auditLog.targetType(),
				auditLog.targetId(),
				auditLog.beforeValue(),
				auditLog.afterValue(),
				auditLog.ipAddress(),
				auditLog.userAgent(),
				Timestamp.from(auditLog.createdAt()));
		return auditLog;
	}

	public List<AuditLog> findRecent(int limit, int offset) {
		return jdbcTemplate.query("""
				select *
				from audit_logs
				order by created_at desc
				limit ? offset ?
				""", rowMapper(), limit, offset);
	}

	private RowMapper<AuditLog> rowMapper() {
		return (rs, rowNum) -> mapAuditLog(rs);
	}

	private AuditLog mapAuditLog(ResultSet rs) throws SQLException {
		return new AuditLog(
				rs.getObject("id", UUID.class),
				rs.getObject("actor_user_id", UUID.class),
				rs.getString("action"),
				rs.getString("target_type"),
				rs.getString("target_id"),
				rs.getString("before_value"),
				rs.getString("after_value"),
				rs.getString("ip_address"),
				rs.getString("user_agent"),
				toInstant(rs, "created_at"));
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
