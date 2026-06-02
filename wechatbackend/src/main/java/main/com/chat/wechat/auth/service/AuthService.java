package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.auth.dto.AuthResponse;
import main.com.chat.wechat.auth.dto.AuthUserResponse;
import main.com.chat.wechat.auth.dto.LoginRequest;
import main.com.chat.wechat.auth.dto.RegisterRequest;
import main.com.chat.wechat.auth.model.RefreshToken;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.common.security.JwtProperties;
import main.com.chat.wechat.common.security.JwtToken;
import main.com.chat.wechat.common.security.JwtTokenService;
import main.com.chat.wechat.role.model.Role;
import main.com.chat.wechat.role.repository.RoleRepository;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final JwtTokenService jwtTokenService;
	private final JwtProperties jwtProperties;
	private final PasswordEncoder passwordEncoder;
	private final RoleRepository roleRepository;
	private final AuditLogService auditLogService;

	public AuthService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			JwtTokenService jwtTokenService,
			JwtProperties jwtProperties,
			PasswordEncoder passwordEncoder,
			RoleRepository roleRepository,
			AuditLogService auditLogService) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.jwtTokenService = jwtTokenService;
		this.jwtProperties = jwtProperties;
		this.passwordEncoder = passwordEncoder;
		this.roleRepository = roleRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String username = request.username().trim();
		String email = request.email().trim().toLowerCase(Locale.ROOT);

		if (userRepository.existsByUsernameOrEmail(username, email)) {
			throw new ApiException(HttpStatus.CONFLICT, "Username or email already exists");
		}

		Instant now = Instant.now();
		String displayName = StringUtils.hasText(request.displayName()) ? request.displayName().trim() : username;
		User user = new User(
				UUID.randomUUID(),
				username,
				email,
				passwordEncoder.encode(request.password()),
				displayName,
				null,
				"OFFLINE",
				"USER",
				true,
				"ACTIVE",
				false,
				0,
				null,
				0,
				null,
				null,
				now,
				now);

		userRepository.save(user);
		Role userRole = roleRepository.findByCode("USER")
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Default role USER is not configured"));
		roleRepository.replaceUserRoles(user.id(), Set.of(userRole.id()), null, now);
		auditLogService.log("AUTH_REGISTER", "USER", user.id().toString(), null, "{\"username\":\"" + username + "\"}");
		return issueTokens(user);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByUsernameOrEmail(request.identifier().trim())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"));

		if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
			userRepository.recordLoginFailure(user.id(), Instant.now());
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
		}

		if (!user.active()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "User account is not active");
		}

		userRepository.recordLoginSuccess(user.id(), Instant.now());
		return issueTokens(user);
	}

	@Transactional
	public AuthResponse refresh(String rawRefreshToken) {
		Instant now = Instant.now();
		String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
		RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

		if (storedToken.revokedAt() != null) {
			refreshTokenRepository.revokeAllForUser(storedToken.userId(), now);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
		}
		if (!storedToken.expiresAt().isAfter(now)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
		}

		User user = userRepository.findById(storedToken.userId())
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

		GeneratedRefreshToken newRefreshToken = createRefreshToken(user.id(), now);
		refreshTokenRepository.revoke(tokenHash, now, newRefreshToken.tokenHash());
		return issueTokens(user, newRefreshToken);
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
		refreshTokenRepository.revoke(tokenHash, Instant.now(), null);
	}

	private AuthResponse issueTokens(User user) {
		return issueTokens(user, createRefreshToken(user.id(), Instant.now()));
	}

	private AuthResponse issueTokens(User user, GeneratedRefreshToken generatedRefreshToken) {
		List<String> roles = roleRepository.findRoleCodesByUserId(user.id());
		List<String> permissions = roleRepository.findPermissionCodesByUserId(user.id());
		JwtToken accessToken = jwtTokenService.createAccessToken(user, roles, permissions);
		return new AuthResponse(
				accessToken.value(),
				generatedRefreshToken.rawToken(),
				"Bearer",
				accessToken.expiresAt(),
				generatedRefreshToken.expiresAt(),
				AuthUserResponse.from(user, roles));
	}

	private GeneratedRefreshToken createRefreshToken(UUID userId, Instant now) {
		String rawToken = refreshTokenGenerator.generate();
		String tokenHash = refreshTokenGenerator.hash(rawToken);
		Instant expiresAt = now.plus(jwtProperties.refreshTokenTtl());
		refreshTokenRepository.save(new RefreshToken(
				UUID.randomUUID(),
				userId,
				tokenHash,
				expiresAt,
				null,
				now,
				null));
		return new GeneratedRefreshToken(rawToken, tokenHash, expiresAt);
	}

	private record GeneratedRefreshToken(String rawToken, String tokenHash, Instant expiresAt) {
	}
}
