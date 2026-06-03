package main.com.chat.wechat.role.repository;

import main.com.chat.wechat.role.model.Permission;
import main.com.chat.wechat.role.model.RolePermission;
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
public class RolePermissionRepository {
	private final JdbcTemplate jdbcTemplate;

	public RolePermissionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<RolePermission> findByRoleId(UUID roleId) {
		return jdbcTemplate.query("""
				select *
				from role_permissions
				where role_id = ?
				""", rolePermissionRowMapper(), roleId);
	}

	public List<Permission> findPermissionsByRoleId(UUID roleId) {
		return jdbcTemplate.query("""
				select p.*
				from role_permissions rp
				join permissions p on p.id = rp.permission_id
				where rp.role_id = ?
				order by p.code
				""", permissionRowMapper(), roleId);
	}

	private RowMapper<RolePermission> rolePermissionRowMapper() {
		return (rs, rowNum) -> new RolePermission(
				rs.getObject("role_id", UUID.class),
				rs.getObject("permission_id", UUID.class));
	}

	private RowMapper<Permission> permissionRowMapper() {
		return (rs, rowNum) -> mapPermission(rs);
	}

	private Permission mapPermission(ResultSet rs) throws SQLException {
		return new Permission(
				rs.getObject("id", UUID.class),
				rs.getString("code"),
				rs.getString("name"),
				rs.getString("description"),
				toInstant(rs, "created_at"));
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
