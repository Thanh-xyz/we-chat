package main.com.chat.wechat.user.service;

import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.role.repository.RoleRepository;
import main.com.chat.wechat.user.dto.UpdateProfileRequest;
import main.com.chat.wechat.user.dto.UserResponse;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;

	public UserService(UserRepository userRepository, RoleRepository roleRepository) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
	}

	public UserResponse me(UUID userId) {
		User user = findActiveUser(userId);
		return UserResponse.from(user, roleRepository.findRoleCodesByUserId(user.id()));
	}

	@Transactional
	public UserResponse updateMe(UUID userId, UpdateProfileRequest request) {
		User user = findActiveUser(userId);
		String displayName = StringUtils.hasText(request.displayName())
				? request.displayName().trim()
				: user.displayName();
		String avatarUrl = StringUtils.hasText(request.avatarUrl())
				? request.avatarUrl().trim()
				: user.avatarUrl();
		User updated = userRepository.updateProfile(user.id(), displayName, avatarUrl, Instant.now());
		return UserResponse.from(updated, roleRepository.findRoleCodesByUserId(updated.id()));
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
	}
}
