package main.com.chat.wechat.cleanup;

import main.com.chat.wechat.cleanup.job.AuditLogRetentionJob;
import main.com.chat.wechat.cleanup.job.ExpiredEmailVerificationCleanupJob;
import main.com.chat.wechat.cleanup.job.ExpiredPasswordResetCleanupJob;
import main.com.chat.wechat.cleanup.job.ExpiredRefreshTokenCleanupJob;
import main.com.chat.wechat.cleanup.job.NotificationCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupStaticConfigurationTest {
	@Test
	void schedulingIsEnabledAndEveryConcernHasItsOwnScheduledJob() throws Exception {
		assertThat(CleanupSchedulingConfig.class).hasAnnotation(EnableScheduling.class);
		for (Class<?> jobType : List.of(
				ExpiredRefreshTokenCleanupJob.class,
				ExpiredPasswordResetCleanupJob.class,
				ExpiredEmailVerificationCleanupJob.class,
				NotificationCleanupJob.class,
				AuditLogRetentionJob.class)) {
			assertThat(jobType.getMethod("runCleanup").getAnnotation(Scheduled.class)).isNotNull();
		}
	}

	@Test
	void applicationConfigUsesExplicitEnvironmentOverridableDurationsAndSafeDefaults() throws Exception {
		Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
			properties.load(reader);
		}

		assertThat(properties.getProperty("app.cleanup.refresh-tokens.retention"))
				.isEqualTo("${CLEANUP_REFRESH_TOKENS_RETENTION:P30D}");
		assertThat(properties.getProperty("app.cleanup.password-reset-tokens.retention"))
				.isEqualTo("${CLEANUP_PASSWORD_RESET_TOKENS_RETENTION:P1D}");
		assertThat(properties.getProperty("app.cleanup.email-verification-tokens.retention"))
				.isEqualTo("${CLEANUP_EMAIL_VERIFICATION_TOKENS_RETENTION:P7D}");
		assertThat(properties.getProperty("app.cleanup.notifications.retention"))
				.isEqualTo("${CLEANUP_NOTIFICATIONS_RETENTION:P180D}");
		assertThat(properties.getProperty("app.cleanup.audit-logs.retention"))
				.isEqualTo("${CLEANUP_AUDIT_LOGS_RETENTION:P180D}");
		assertThat(properties.getProperty("app.cleanup.audit-logs.security-retention"))
				.isEqualTo("${CLEANUP_AUDIT_LOGS_SECURITY_RETENTION:P365D}");
	}

	@Test
	void migrationAddsOnlyIndexesNeededByCleanupPredicates() throws Exception {
		String migration = Files.readString(Path.of(
				"src/main/resources/db/migration/V16__data_retention_cleanup_indexes.sql"));
		String authMigration = Files.readString(Path.of(
				"src/main/resources/db/migration/V9__production_auth_hardening.sql"));
		String initialMigration = Files.readString(Path.of(
				"src/main/resources/db/migration/V1__create_auth_tables.sql"));
		String auditMigration = Files.readString(Path.of(
				"src/main/resources/db/migration/V13__phase8_audit_logging_compliance.sql"));

		assertThat(migration)
				.contains("refresh_tokens (revoked_at)")
				.contains("password_reset_tokens (used_at)")
				.contains("email_verification_tokens (used_at)")
				.contains("notifications (deleted_at)")
				.contains("notifications (read_at)")
				.doesNotContain("date(", "now()", "current_timestamp");
		assertThat(initialMigration).contains("refresh_tokens (expires_at)");
		assertThat(authMigration)
				.contains("password_reset_tokens (expires_at)")
				.contains("email_verification_tokens (expires_at)");
		assertThat(auditMigration)
				.contains("audit_logs (created_at desc)")
				.contains("audit_logs (action, created_at desc)");
	}
}
