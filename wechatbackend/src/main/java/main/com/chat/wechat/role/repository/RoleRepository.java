package main.com.chat.wechat.role.repository;

import main.com.chat.wechat.role.model.Permission;
import main.com.chat.wechat.role.model.Role;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class RoleRepository {
	private final JdbcTemplate jdbcTemplate;

	public RoleRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<Role> findAll() {
		return jdbcTemplate.query("""
				select *
				from roles
				where deleted_at is null
				order by system_role desc, code asc
				""", roleRowMapper());
	}

	public Optional<Role> findById(UUID id) {
		try {
			Role role = jdbcTemplate.queryForObject("""
					select *
					from roles
					where id = ? and deleted_at is null
					""", roleRowMapper(), id);
			return Optional.ofNullable(role);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Optional<Role> findByCode(String code) {
		try {
			Role role = jdbcTemplate.queryForObject("""
					select *
					from roles
					where lower(code) = lower(?) and deleted_at is null
					""", roleRowMapper(), code);
			return Optional.ofNullable(role);
		} catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	public Role save(Role role) {
		jdbcTemplate.update("""
				insert into roles (id, code, name, description, system_role, deleted_at, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				""",
				role.id(),
				role.code(),
				role.name(),
				role.description(),
				role.systemRole(),
				toTimestamp(role.deletedAt()),
				Timestamp.from(role.createdAt()),
				Timestamp.from(role.updatedAt()));
		return role;
	}

	public Role update(UUID id, String code, String name, String description, Instant updatedAt) {
		jdbcTemplate.update("""
				update roles
				set code = ?, name = ?, description = ?, updated_at = ?
				where id = ? and deleted_at is null
				""", code, name, description, Timestamp.from(updatedAt), id);
		return findById(id).orElseThrow();
	}

	public void softDelete(UUID id, Instant deletedAt) {
		jdbcTemplate.update("""
				update roles
				set deleted_at = ?, updated_at = ?
				where id = ? and system_role = false and deleted_at is null
				""", Timestamp.from(deletedAt), Timestamp.from(deletedAt), id);
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

	public List<Permission> findPermissionsByRoleId(UUID roleId) {
		return jdbcTemplate.query("""
				select p.*
				from role_permissions rp
				join permissions p on p.id = rp.permission_id
				where rp.role_id = ?
				order by p.code
				""", permissionRowMapper(), roleId);
	}

	public void replaceUserRoles(UUID userId, Set<UUID> roleIds, UUID assignedBy, Instant assignedAt) {
		jdbcTemplate.update("delete from user_roles where user_id = ?", userId);
		for (UUID roleId : roleIds) {
			jdbcTemplate.update("""
					insert into user_roles (user_id, role_id, assigned_by, assigned_at)
					values (?, ?, ?, ?)
					""", userId, roleId, assignedBy, Timestamp.from(assignedAt));
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

	public List<Permission> findAllPermissions() {
		return jdbcTemplate.query("""
				select *
				from permissions
				order by code
				""", permissionRowMapper());
	}

	private RowMapper<Role> roleRowMapper() {
		return (rs, rowNum) -> mapRole(rs);
	}

	private RowMapper<Permission> permissionRowMapper() {
		return (rs, rowNum) -> mapPermission(rs);
	}

	private Role mapRole(ResultSet rs) throws SQLException {
		return new Role(
				rs.getObject("id", UUID.class),
				rs.getString("code"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getBoolean("system_role"),
				toInstant(rs, "deleted_at"),
				toInstant(rs, "created_at"),
				toInstant(rs, "updated_at"));
	}

	private Permission mapPermission(ResultSet rs) throws SQLException {
		return new Permission(
				rs.getObject("id", UUID.class),
				rs.getString("code"),
				rs.getString("name"),
				rs.getString("description"),
				toInstant(rs, "created_at"));
	}

	private Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
