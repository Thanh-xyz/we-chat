package main.com.chat.wechat.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import main.com.chat.wechat.user.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SmtpAuthEmailServiceTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

	@Test
	void smtpMessagesContainUserLinksButApplicationLogDoesNot() {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		AuthEmailProperties emailProperties = new AuthEmailProperties(
				"https://chat.example.com/verify-email?token=",
				"https://chat.example.com/reset-password?token=");
		AuthMailProperties mailProperties = new AuthMailProperties(
				"smtp.example.com",
				587,
				"mailer",
				"mail-secret",
				"no-reply@example.com");
		SmtpAuthEmailService service = new SmtpAuthEmailService(mailSender, emailProperties, mailProperties);
		String verificationToken = "raw-verification-token-for-smtp";
		String resetToken = "raw-reset-token-for-smtp";
		Instant expiresAt = Instant.parse("2026-09-11T12:30:00Z");
		Logger logger = (Logger) LoggerFactory.getLogger(SmtpAuthEmailService.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		try {
			service.sendVerificationEmail(user(), verificationToken, expiresAt);
			service.sendPasswordResetEmail(user(), resetToken, expiresAt);
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}

		ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, times(2)).send(messageCaptor.capture());
		assertThat(messageCaptor.getAllValues())
				.allSatisfy(message -> {
					assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
					assertThat(message.getTo()).containsExactly("smtp-user@example.com");
				});
		assertThat(messageCaptor.getAllValues().get(0).getText())
				.contains("https://chat.example.com/verify-email?token=" + verificationToken);
		assertThat(messageCaptor.getAllValues().get(1).getText())
				.contains("https://chat.example.com/reset-password?token=" + resetToken);
		assertThat(appender.list)
				.extracting(ILoggingEvent::getFormattedMessage)
				.noneMatch(log -> log.contains(verificationToken)
						|| log.contains(resetToken)
						|| log.contains("verify-email?token=")
						|| log.contains("reset-password?token="));
	}

	private User user() {
		Instant now = Instant.parse("2026-09-11T12:00:00Z");
		return new User(
				USER_ID,
				"smtp-user",
				"smtp-user@example.com",
				"password-hash-not-for-logs",
				"SMTP User",
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
