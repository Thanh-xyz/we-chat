package main.com.chat.wechat.role.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.role.dto.RoleRequest;
import main.com.chat.wechat.role.dto.RoleResponse;
import main.com.chat.wechat.role.service.RoleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/roles", "/admin/roles"})
public class AdminRoleController {
	private final RoleService roleService;

	public AdminRoleController(RoleService roleService) {
		this.roleService = roleService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_READ')")
	public List<RoleResponse> list() {
		return roleService.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('ROLE_WRITE')")
	public RoleResponse create(@Valid @RequestBody RoleRequest request) {
		return roleService.create(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_WRITE')")
	public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
		return roleService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('ROLE_WRITE')")
	public void delete(@PathVariable UUID id) {
		roleService.delete(id);
	}
}
