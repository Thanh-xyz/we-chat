package main.com.chat.wechat.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import main.com.chat.wechat.auth.model.RefreshToken;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.common.security.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
	private final RefreshTokenRepository refreshTokenRepository;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final JwtProperties jwtProperties;

	public RefreshTokenService(
			RefreshTokenRepository refreshTokenRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.jwtProperties = jwtProperties;
	}

	public GeneratedRefreshToken create(UUID userId, Instant now, HttpServletRequest request) {
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
				null,
				deviceInfo(request),
				ipAddress(request)));
		return new GeneratedRefreshToken(rawToken, tokenHash, expiresAt);
	}

	public Optional<RefreshToken> findByRawToken(String rawToken) {
		return refreshTokenRepository.findByTokenHash(hash(rawToken));
	}

	public String hash(String rawToken) {
		return refreshTokenGenerator.hash(rawToken);
	}

	public void revokeByHash(String tokenHash, Instant revokedAt, String replacedByTokenHash) {
		refreshTokenRepository.revoke(tokenHash, revokedAt, replacedByTokenHash);
	}

	public void revokeRaw(String rawToken, Instant revokedAt) {
		refreshTokenRepository.revoke(hash(rawToken), revokedAt, null);
	}

	public void revokeAllForUser(UUID userId, Instant revokedAt) {
		refreshTokenRepository.revokeAllForUser(userId, revokedAt);
	}

	private String deviceInfo(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String userAgent = request.getHeader("User-Agent");
		return StringUtils.hasText(userAgent) ? userAgent : null;
	}

	private String ipAddress(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwardedFor)) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	public record GeneratedRefreshToken(String rawToken, String tokenHash, Instant expiresAt) {
	}
}
