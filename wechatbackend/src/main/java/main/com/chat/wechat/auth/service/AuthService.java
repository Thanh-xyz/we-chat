package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.auth.dto.AuthResponse;
import main.com.chat.wechat.auth.dto.AuthUserResponse;
import main.com.chat.wechat.auth.dto.LoginRequest;
import main.com.chat.wechat.auth.dto.RegisterRequest;
import main.com.chat.wechat.auth.model.RefreshToken;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.common.security.JwtProperties;
import main.com.chat.wechat.common.security.JwtToken;
import main.com.chat.wechat.common.security.JwtTokenService;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final JwtTokenService jwtTokenService;
	private final JwtProperties jwtProperties;
	private final PasswordEncoder passwordEncoder;

	public AuthService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			JwtTokenService jwtTokenService,
			JwtProperties jwtProperties,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.jwtTokenService = jwtTokenService;
		this.jwtProperties = jwtProperties;
		this.passwordEncoder = passwordEncoder;
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
				now,
				now);

		userRepository.save(user);
		return issueTokens(user);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByUsernameOrEmail(request.identifier().trim())
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"));

		if (!user.enabled()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "User account is disabled");
		}

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
				.filter(User::enabled)
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
		JwtToken accessToken = jwtTokenService.createAccessToken(user);
		return new AuthResponse(
				accessToken.value(),
				generatedRefreshToken.rawToken(),
				"Bearer",
				accessToken.expiresAt(),
				generatedRefreshToken.expiresAt(),
				AuthUserResponse.from(user));
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
