package main.com.chat.wechat.role.repository;

import main.com.chat.wechat.role.model.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class UserRoleRepository {
	private final JdbcTemplate jdbcTemplate;

	public UserRoleRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<UserRole> findByUserId(UUID userId) {
		return jdbcTemplate.query("""
				select *
				from user_roles
				where user_id = ?
				order by assigned_at
				""", rowMapper(), userId);
	}

	public List<String> findRoleCodesByUserId(UUID userId) {
		return jdbcTemplate.queryForList("""
				select r.code
				from user_roles ur
				join roles r on r.id = ur.role_id
				where ur.user_id = ? and r.deleted_at is null
				order by r.code
				""", String.class, userId);
	}

	public List<String> findPermissionCodesByUserId(UUID userId) {
		return jdbcTemplate.queryForList("""
				select distinct p.code
				from user_roles ur
				join roles r on r.id = ur.role_id
				join role_permissions rp on rp.role_id = r.id
				join permissions p on p.id = rp.permission_id
				where ur.user_id = ? and r.deleted_at is null
				order by p.code
				""", String.class, userId);
	}

	public void assign(UUID userId, UUID roleId, UUID assignedBy, Instant assignedAt) {
		jdbcTemplate.update("""
				insert into user_roles (user_id, role_id, assigned_by, assigned_at)
				values (?, ?, ?, ?)
				on conflict (user_id, role_id) do update
				set assigned_by = excluded.assigned_by,
				    assigned_at = excluded.assigned_at
				""", userId, roleId, assignedBy, Timestamp.from(assignedAt));
	}

	public void remove(UUID userId, UUID roleId) {
		jdbcTemplate.update("""
				delete from user_roles
				where user_id = ? and role_id = ?
				""", userId, roleId);
	}

	public void replace(UUID userId, Set<UUID> roleIds, UUID assignedBy, Instant assignedAt) {
		jdbcTemplate.update("delete from user_roles where user_id = ?", userId);
		for (UUID roleId : roleIds) {
			assign(userId, roleId, assignedBy, assignedAt);
		}
	}

	public boolean isRoleAssigned(UUID roleId) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from user_roles
				where role_id = ?
				""", Integer.class, roleId);
		return count != null && count > 0;
	}

	public int countActiveUsersByRoleCode(String roleCode) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from user_roles ur
				join roles r on r.id = ur.role_id
				join users u on u.id = ur.user_id
				where r.code = ?
				  and r.deleted_at is null
				  and u.deleted_at is null
				  and u.account_status = 'ACTIVE'
				""", Integer.class, roleCode);
		return count == null ? 0 : count;
	}

	private RowMapper<UserRole> rowMapper() {
		return (rs, rowNum) -> mapUserRole(rs);
	}

	private UserRole mapUserRole(ResultSet rs) throws SQLException {
		return new UserRole(
				rs.getObject("user_id", UUID.class),
				rs.getObject("role_id", UUID.class),
				rs.getObject("assigned_by", UUID.class),
				toInstant(rs, "assigned_at"));
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
