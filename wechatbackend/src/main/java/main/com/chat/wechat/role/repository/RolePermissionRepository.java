package main.com.chat.wechat.role.repository;

import main.com.chat.wechat.role.model.Permission;
import main.com.chat.wechat.role.model.RolePermission;
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

	public Map<UUID, List<Permission>> findPermissionsByRoleIds(List<UUID> roleIds) {
		if (roleIds == null || roleIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<UUID, List<Permission>> result = new LinkedHashMap<>();
		roleIds.forEach(roleId -> result.put(roleId, new ArrayList<>()));
		jdbcTemplate.query("""
				select rp.role_id, p.*
				from role_permissions rp
				join permissions p on p.id = rp.permission_id
				where rp.role_id in (%s)
				order by rp.role_id, p.code
				""".formatted(placeholders(roleIds.size())),
				(RowCallbackHandler) rs -> {
					UUID roleId = rs.getObject("role_id", UUID.class);
					result.computeIfAbsent(roleId, ignored -> new ArrayList<>()).add(mapPermission(rs));
				},
				roleIds.toArray());
		return result;
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

	private String placeholders(int count) {
		return String.join(",", Collections.nCopies(count, "?"));
	}
}
