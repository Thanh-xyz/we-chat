package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Profile("(local | test) & !prod")
public class LoggingAuthEmailService implements AuthEmailService {
	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAuthEmailService.class);

	@Override
	public void sendVerificationEmail(User user, String token, Instant expiresAt) {
		LOGGER.info(
				"Authentication email event=email_verification delivery=logging userId={} email={} expiresAt={}",
				user.id(),
				user.email(),
				expiresAt);
	}

	@Override
	public void sendPasswordResetEmail(User user, String token, Instant expiresAt) {
		LOGGER.info(
				"Authentication email event=password_reset delivery=logging userId={} email={} expiresAt={}",
				user.id(),
				user.email(),
				expiresAt);
	}
}
