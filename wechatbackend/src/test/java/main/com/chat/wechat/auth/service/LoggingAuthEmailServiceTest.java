package main.com.chat.wechat.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import main.com.chat.wechat.user.model.User;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAuthEmailServiceTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000041");

	@Test
	void localEmailMetadataNeverIncludesTokensUrlsOrPasswordHash() {
		String verificationToken = "raw-verification-token-R4";
		String resetToken = "raw-password-reset-token-R4";
		String passwordHash = "$2a$12$secret-password-hash-R4";
		Instant expiresAt = Instant.parse("2026-09-11T12:30:00Z");
		User user = user(passwordHash);
		Logger logger = (Logger) LoggerFactory.getLogger(LoggingAuthEmailService.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		try {
			LoggingAuthEmailService service = new LoggingAuthEmailService();
			service.sendVerificationEmail(user, verificationToken, expiresAt);
			service.sendPasswordResetEmail(user, resetToken, expiresAt);
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}

		String logs = renderedLogs(appender);
		assertThat(logs)
				.contains("event=email_verification", "event=password_reset", USER_ID.toString(), expiresAt.toString())
				.doesNotContain(
						verificationToken,
						resetToken,
						"verificationUrl",
						"resetUrl",
						"http://localhost:5173/verify-email?token=" + verificationToken,
						"http://localhost:5173/reset-password?token=" + resetToken,
						passwordHash);
	}

	private String renderedLogs(ListAppender<ILoggingEvent> appender) {
		return appender.list.stream()
				.map(event -> event.getFormattedMessage()
						+ (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
				.collect(Collectors.joining("\n"));
	}

	private User user(String passwordHash) {
		Instant now = Instant.parse("2026-09-11T12:00:00Z");
		return new User(
				USER_ID,
				"safe-log-user",
				"safe-log-user@example.com",
				passwordHash,
				"Safe Log User",
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
	}
}
