package main.com.chat.wechat.cleanup.job;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import main.com.chat.wechat.audit.repository.AuditLogRepository;
import main.com.chat.wechat.auth.repository.EmailVerificationTokenRepository;
import main.com.chat.wechat.auth.repository.PasswordResetTokenRepository;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.cleanup.CleanupJobExecutor;
import main.com.chat.wechat.cleanup.CleanupProperties;
import main.com.chat.wechat.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CleanupJobsTest {
	private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

	@Mock
	private RefreshTokenRepository refreshTokens;
	@Mock
	private PasswordResetTokenRepository passwordResetTokens;
	@Mock
	private EmailVerificationTokenRepository emailVerificationTokens;
	@Mock
	private NotificationRepository notifications;
	@Mock
	private AuditLogRepository auditLogs;

	private CleanupProperties properties;
	private CleanupJobExecutor executor;
	private Clock clock;

	@BeforeEach
	void setUp() {
		properties = new CleanupProperties(
				policy(Duration.ofDays(30), 101),
				policy(Duration.ofDays(1), 102),
				policy(Duration.ofDays(7), 103),
				policy(Duration.ofDays(180), 104),
				new CleanupProperties.AuditPolicy(
						true,
						Duration.ofDays(180),
						Duration.ofDays(365),
						Set.of("AUTH_LOGIN_FAILED", "SECURITY_ACCESS_DENIED"),
						105,
						2,
						Duration.ofDays(1),
						Duration.ZERO));
		executor = new CleanupJobExecutor(new SimpleMeterRegistry());
		clock = Clock.fixed(NOW, ZoneOffset.UTC);
	}

	@Test
	void refreshJobUsesExplicitClockAndRetentionCutoff() {
		new ExpiredRefreshTokenCleanupJob(refreshTokens, properties, executor, clock).runCleanup();

		Instant cutoff = NOW.minus(Duration.ofDays(30));
		verify(refreshTokens).deleteExpiredOrRevokedBefore(cutoff, cutoff, 101);
	}

	@Test
	void passwordResetJobUsesItsOwnRetentionPolicy() {
		new ExpiredPasswordResetCleanupJob(passwordResetTokens, properties, executor, clock).runCleanup();

		Instant cutoff = NOW.minus(Duration.ofDays(1));
		verify(passwordResetTokens).deleteExpiredOrUsedBefore(cutoff, cutoff, 102);
	}

	@Test
	void emailVerificationJobUsesItsOwnRetentionPolicy() {
		new ExpiredEmailVerificationCleanupJob(emailVerificationTokens, properties, executor, clock).runCleanup();

		Instant cutoff = NOW.minus(Duration.ofDays(7));
		verify(emailVerificationTokens).deleteExpiredOrUsedBefore(cutoff, cutoff, 103);
	}

	@Test
	void notificationJobUsesReadAndDeletionRetentionCutoff() {
		new NotificationCleanupJob(notifications, properties, executor, clock).runCleanup();

		Instant cutoff = NOW.minus(Duration.ofDays(180));
		verify(notifications).deleteReadOrSoftDeletedBefore(cutoff, cutoff, 104);
	}

	@Test
	void auditJobUsesLongerCutoffForSecurityActions() {
		new AuditLogRetentionJob(auditLogs, properties, executor, clock).runCleanup();

		verify(auditLogs).deleteBefore(
				NOW.minus(Duration.ofDays(180)),
				NOW.minus(Duration.ofDays(365)),
				Set.of("AUTH_LOGIN_FAILED", "SECURITY_ACCESS_DENIED"),
				105);
	}

	private CleanupProperties.Policy policy(Duration retention, int batchSize) {
		return new CleanupProperties.Policy(
				true,
				retention,
				batchSize,
				2,
				Duration.ofHours(1),
				Duration.ZERO);
	}
}

