package main.com.chat.wechat.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import main.com.chat.wechat.auth.dto.AuthResponse;
import main.com.chat.wechat.auth.dto.ChangePasswordRequest;
import main.com.chat.wechat.auth.dto.ForgotPasswordRequest;
import main.com.chat.wechat.auth.dto.LoginRequest;
import main.com.chat.wechat.auth.dto.RegisterRequest;
import main.com.chat.wechat.auth.dto.RegisterResponse;
import main.com.chat.wechat.auth.dto.ResendVerificationRequest;
import main.com.chat.wechat.auth.dto.ResetPasswordRequest;
import main.com.chat.wechat.auth.dto.VerifyEmailRequest;
import main.com.chat.wechat.auth.model.EmailVerificationToken;
import main.com.chat.wechat.auth.model.PasswordResetToken;
import main.com.chat.wechat.auth.model.RefreshToken;
import main.com.chat.wechat.auth.repository.EmailVerificationTokenRepository;
import main.com.chat.wechat.auth.repository.PasswordResetTokenRepository;
import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.common.security.JwtProperties;
import main.com.chat.wechat.common.security.JwtToken;
import main.com.chat.wechat.common.security.JwtTokenService;
import main.com.chat.wechat.common.security.LoginSecurityProperties;
import main.com.chat.wechat.common.security.RbacProperties;
import main.com.chat.wechat.role.model.Role;
import main.com.chat.wechat.role.repository.RoleRepository;
import main.com.chat.wechat.role.repository.UserRoleRepository;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository userRepository;
	private final RefreshTokenService refreshTokenService;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final JwtTokenService jwtTokenService;
	private final JwtProperties jwtProperties;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicyValidator passwordPolicyValidator;
	private final AuthEmailService authEmailService;
	private final RoleRepository roleRepository;
	private final UserRoleRepository userRoleRepository;
	private final AuditLogService auditLogService;
	private final AuditJsonWriter auditJsonWriter;
	private final RbacProperties rbacProperties;
	private final LoginSecurityProperties loginSecurityProperties;

	public AuthService(
			UserRepository userRepository,
			RefreshTokenService refreshTokenService,
			PasswordResetTokenRepository passwordResetTokenRepository,
			EmailVerificationTokenRepository emailVerificationTokenRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			JwtTokenService jwtTokenService,
			JwtProperties jwtProperties,
			PasswordEncoder passwordEncoder,
			PasswordPolicyValidator passwordPolicyValidator,
			AuthEmailService authEmailService,
			RoleRepository roleRepository,
			UserRoleRepository userRoleRepository,
			AuditLogService auditLogService,
			AuditJsonWriter auditJsonWriter,
			RbacProperties rbacProperties,
			LoginSecurityProperties loginSecurityProperties) {
		this.userRepository = userRepository;
		this.refreshTokenService = refreshTokenService;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.jwtTokenService = jwtTokenService;
		this.jwtProperties = jwtProperties;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicyValidator = passwordPolicyValidator;
		this.authEmailService = authEmailService;
		this.roleRepository = roleRepository;
		this.userRoleRepository = userRoleRepository;
		this.auditLogService = auditLogService;
		this.auditJsonWriter = auditJsonWriter;
		this.rbacProperties = rbacProperties;
		this.loginSecurityProperties = loginSecurityProperties;
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String username = request.username().trim();
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		passwordPolicyValidator.validate(request.password());

		if (userRepository.existsByUsername(username)) {
			throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
		}
		if (userRepository.existsByEmail(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
		}

		Instant now = Instant.now();
		String defaultRoleCode = rbacProperties.defaultUserRole();
		String displayName = StringUtils.hasText(request.displayName()) ? request.displayName().trim() : username;
		User user = new User(
				UUID.randomUUID(),
				username,
				email,
				passwordEncoder.encode(request.password()),
				displayName,
				null,
				"OFFLINE",
				defaultRoleCode,
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
		Role userRole = roleRepository.findByCode(defaultRoleCode)
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Default role is not configured"));
		userRoleRepository.replace(user.id(), Set.of(userRole.id()), null, now);
		issueEmailVerification(user, now);
		auditLogService.log("AUTH_REGISTER", "USER", user.id().toString(), null, auditJsonWriter.write(new RegisterAuditValue(username)));
		return new RegisterResponse(user.id(), user.email(), user.emailVerified());
	}

	@Transactional
	public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
		Instant now = Instant.now();
		String identifier = request.identifier().trim();
		User user = userRepository.findByUsernameOrEmail(identifier)
				.orElseThrow(() -> {
					auditLogService.log("LOGIN_FAILED", "USER", null, null, auditJsonWriter.write(new LoginAuditValue(identifier)), httpRequest);
					return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
				});

		if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
			auditLogService.log("ACCOUNT_LOCKED", "USER", user.id().toString(), null, null, httpRequest);
			throw new ApiException(HttpStatus.LOCKED, "User account is temporarily locked");
		}

		if (!user.active() || !user.enabled()) {
			auditLogService.log("LOGIN_FAILED", "USER", user.id().toString(), null, auditJsonWriter.write(new LoginAuditValue(identifier)), httpRequest);
			throw new ApiException(HttpStatus.FORBIDDEN, "User account is not active");
		}

		if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
			User failedUser = userRepository.recordLoginFailure(
					user.id(),
					now,
					loginSecurityProperties.maxFailedLoginAttempts(),
					now.plus(loginSecurityProperties.lockDuration()));
			auditLogService.log("LOGIN_FAILED", "USER", user.id().toString(), null, auditJsonWriter.write(new LoginAuditValue(identifier)), httpRequest);
			if (failedUser.lockedUntil() != null && failedUser.lockedUntil().isAfter(now)) {
				auditLogService.log("ACCOUNT_LOCKED", "USER", user.id().toString(), null, null, httpRequest);
				throw new ApiException(HttpStatus.LOCKED, "User account is temporarily locked");
			}
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
		}

		if (loginSecurityProperties.requireEmailVerified() && !user.emailVerified()) {
			auditLogService.log("LOGIN_FAILED", "USER", user.id().toString(), null, auditJsonWriter.write(new LoginAuditValue(identifier)), httpRequest);
			throw new ApiException(HttpStatus.FORBIDDEN, "Email verification is required");
		}

		userRepository.recordLoginSuccess(user.id(), now);
		auditLogService.log("LOGIN_SUCCESS", "USER", user.id().toString(), null, null, httpRequest);
		return issueTokens(user, httpRequest);
	}

	@Transactional
	public AuthResponse refresh(String rawRefreshToken, HttpServletRequest request) {
		Instant now = Instant.now();
		String tokenHash = refreshTokenService.hash(rawRefreshToken);
		RefreshToken storedToken = refreshTokenService.findByRawToken(rawRefreshToken)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

		if (storedToken.revokedAt() != null) {
			refreshTokenService.revokeAllForUser(storedToken.userId(), now);
			userRepository.incrementTokenVersion(storedToken.userId(), now);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
		}
		if (!storedToken.expiresAt().isAfter(now)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
		}

		User user = userRepository.findById(storedToken.userId())
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

		RefreshTokenService.GeneratedRefreshToken newRefreshToken = refreshTokenService.create(user.id(), now, request);
		refreshTokenService.revokeByHash(tokenHash, now, newRefreshToken.tokenHash());
		return issueTokens(user, newRefreshToken);
	}

	@Transactional
	public void logout(String rawRefreshToken, HttpServletRequest request) {
		String tokenHash = refreshTokenService.hash(rawRefreshToken);
		refreshTokenService.revokeRaw(rawRefreshToken, Instant.now());
		auditLogService.log("LOGOUT", "REFRESH_TOKEN", tokenHash, null, null, request);
	}

	@Transactional
	public void logoutAll(AuthenticatedUser principal, HttpServletRequest request) {
		Instant now = Instant.now();
		refreshTokenService.revokeAllForUser(principal.id(), now);
		userRepository.incrementTokenVersion(principal.id(), now);
		auditLogService.log("LOGOUT_ALL", "USER", principal.id().toString(), null, null, request);
	}

	@Transactional
	public void forgotPassword(ForgotPasswordRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		userRepository.findByEmail(email)
				.filter(User::active)
				.ifPresent(user -> {
					Instant now = Instant.now();
					passwordResetTokenRepository.invalidateUnusedForUser(user.id(), now);
					String rawToken = refreshTokenGenerator.generate();
					Instant expiresAt = now.plus(loginSecurityProperties.passwordResetTokenTtl());
					passwordResetTokenRepository.save(new PasswordResetToken(
							UUID.randomUUID(),
							user.id(),
							refreshTokenGenerator.hash(rawToken),
							expiresAt,
							null,
							now));
					LOGGER.info(
							"Password reset token created userId={} now={} expiresAt={} ttl={} systemZone={}",
							user.id(),
							now,
							expiresAt,
							loginSecurityProperties.passwordResetTokenTtl(),
							ZoneId.systemDefault());
					authEmailService.sendPasswordResetEmail(user, rawToken, expiresAt);
				});
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
		passwordPolicyValidator.validate(request.newPassword());
		Instant now = Instant.now();
		String tokenHash = refreshTokenGenerator.hash(request.token());
		PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
				.filter(storedToken -> storedToken.usableAt(now))
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token"));
		User user = userRepository.findById(token.userId())
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token"));
		userRepository.updatePasswordAndIncrementTokenVersion(user.id(), passwordEncoder.encode(request.newPassword()), now);
		passwordResetTokenRepository.markUsed(tokenHash, now);
		refreshTokenService.revokeAllForUser(user.id(), now);
		auditLogService.log("PASSWORD_RESET", "USER", user.id().toString(), null, null, httpRequest);
	}

	@Transactional
	public void verifyEmail(VerifyEmailRequest request, HttpServletRequest httpRequest) {
		Instant now = Instant.now();
		String tokenHash = refreshTokenGenerator.hash(request.token());
		EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> {
					LOGGER.info("Email verification token not found tokenHashPrefix={} now={}", hashPrefix(tokenHash), now);
					return new ApiException(HttpStatus.BAD_REQUEST, "Invalid email verification token");
				});
		LOGGER.info(
				"Email verification token loaded id={} userId={} createdAt={} expiresAt={} usedAt={} now={} systemZone={}",
				token.id(),
				token.userId(),
				token.createdAt(),
				token.expiresAt(),
				token.usedAt(),
				now,
				ZoneId.systemDefault());
		if (token.usedAt() != null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Email verification token has already been used or revoked");
		}
		if (!token.expiresAt().isAfter(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Email verification token has expired");
		}
		User user = userRepository.findById(token.userId())
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired email verification token"));
		userRepository.markEmailVerified(user.id(), now);
		emailVerificationTokenRepository.markUsed(tokenHash, now);
		emailVerificationTokenRepository.invalidateUnusedForUser(user.id(), now);
		auditLogService.log("EMAIL_VERIFIED", "USER", user.id().toString(), null, null, httpRequest);
	}

	@Transactional
	public void resendVerification(ResendVerificationRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		userRepository.findByEmail(email)
				.filter(User::active)
				.filter(user -> !user.emailVerified())
				.ifPresent(user -> {
					Instant now = Instant.now();
					boolean inCooldown = emailVerificationTokenRepository.findLatestCreatedAtByUserId(user.id())
							.map(createdAt -> createdAt.plus(loginSecurityProperties.verificationEmailCooldown()).isAfter(now))
							.orElse(false);
					if (!inCooldown) {
						issueEmailVerification(user, now);
					}
				});
	}

	@Transactional
	public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request, HttpServletRequest httpRequest) {
		passwordPolicyValidator.validate(request.newPassword());
		Instant now = Instant.now();
		User user = userRepository.findById(principal.id())
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required"));
		if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
		}
		userRepository.updatePasswordAndIncrementTokenVersion(user.id(), passwordEncoder.encode(request.newPassword()), now);
		refreshTokenService.revokeAllForUser(user.id(), now);
		auditLogService.log("PASSWORD_CHANGED", "USER", user.id().toString(), null, null, httpRequest);
	}

	private AuthResponse issueTokens(User user, HttpServletRequest request) {
		return issueTokens(user, refreshTokenService.create(user.id(), Instant.now(), request));
	}

	private AuthResponse issueTokens(User user, RefreshTokenService.GeneratedRefreshToken generatedRefreshToken) {
		List<String> roles = userRoleRepository.findRoleCodesByUserId(user.id());
		List<String> permissions = userRoleRepository.findPermissionCodesByUserId(user.id());
		JwtToken accessToken = jwtTokenService.createAccessToken(user, roles, permissions);
		return new AuthResponse(
				accessToken.value(),
				generatedRefreshToken.rawToken(),
				jwtProperties.accessTokenTtl().toSeconds());
	}

	private void issueEmailVerification(User user, Instant now) {
		String rawToken = refreshTokenGenerator.generate();
		Instant expiresAt = now.plus(loginSecurityProperties.emailVerificationTokenTtl());
		emailVerificationTokenRepository.save(new EmailVerificationToken(
				UUID.randomUUID(),
				user.id(),
				refreshTokenGenerator.hash(rawToken),
				expiresAt,
				null,
				now));
		LOGGER.info(
				"Email verification token created userId={} now={} expiresAt={} ttl={} systemZone={}",
				user.id(),
				now,
				expiresAt,
				loginSecurityProperties.emailVerificationTokenTtl(),
				ZoneId.systemDefault());
		authEmailService.sendVerificationEmail(user, rawToken, expiresAt);
	}

	private String hashPrefix(String tokenHash) {
		return tokenHash == null || tokenHash.length() <= 12 ? tokenHash : tokenHash.substring(0, 12);
	}

	private record RegisterAuditValue(String username) {
	}

	private record LoginAuditValue(String identifier) {
	}
}
