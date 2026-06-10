package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LoggingAuthEmailService implements AuthEmailService {
	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAuthEmailService.class);

	private final AuthEmailProperties authEmailProperties;

	public LoggingAuthEmailService(AuthEmailProperties authEmailProperties) {
		this.authEmailProperties = authEmailProperties;
	}

	@Override
	public void sendVerificationEmail(User user, String token, Instant expiresAt) {
		LOGGER.info(
				"Queued email verification for userId={} email={} expiresAt={} token={} verificationUrl={}",
				user.id(),
				user.email(),
				expiresAt,
				token,
				authEmailProperties.emailVerificationUrl() + token);
	}

	@Override
	public void sendPasswordResetEmail(User user, String token, Instant expiresAt) {
		LOGGER.info(
				"Queued password reset for userId={} email={} expiresAt={} token={} resetUrl={}",
				user.id(),
				user.email(),
				expiresAt,
				token,
				authEmailProperties.passwordResetUrl() + token);
	}
}
