package main.com.chat.wechat.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@ConfigurationProperties(prefix = "app.cleanup")
public record CleanupProperties(
		Policy refreshTokens,
		Policy passwordResetTokens,
		Policy emailVerificationTokens,
		Policy notifications,
		AuditPolicy auditLogs) {

	public CleanupProperties {
		refreshTokens = refreshTokens == null
				? new Policy(true, Duration.ofDays(30), 500, 20, Duration.ofHours(1), Duration.ofMinutes(5))
				: refreshTokens;
		passwordResetTokens = passwordResetTokens == null
				? new Policy(true, Duration.ofDays(1), 500, 20, Duration.ofHours(1), Duration.ofMinutes(10))
				: passwordResetTokens;
		emailVerificationTokens = emailVerificationTokens == null
				? new Policy(true, Duration.ofDays(7), 500, 20, Duration.ofHours(1), Duration.ofMinutes(15))
				: emailVerificationTokens;
		notifications = notifications == null
				? new Policy(true, Duration.ofDays(180), 500, 40, Duration.ofDays(1), Duration.ofMinutes(30))
				: notifications;
		auditLogs = auditLogs == null
				? new AuditPolicy(
						true,
						Duration.ofDays(180),
						Duration.ofDays(365),
						defaultSecurityAuditActions(),
						500,
						100,
						Duration.ofDays(1),
						Duration.ofMinutes(45))
				: auditLogs;
	}

	public interface JobPolicy {
		boolean enabled();

		Duration retention();

		int batchSize();

		int maxBatchesPerRun();
	}

	public record Policy(
			boolean enabled,
			Duration retention,
			int batchSize,
			int maxBatchesPerRun,
			Duration fixedDelay,
			Duration initialDelay) implements JobPolicy {
		public Policy {
			validateCommon(retention, batchSize, maxBatchesPerRun, fixedDelay, initialDelay);
		}
	}

	public record AuditPolicy(
			boolean enabled,
			Duration retention,
			Duration securityRetention,
			Set<String> securityActions,
			int batchSize,
			int maxBatchesPerRun,
			Duration fixedDelay,
			Duration initialDelay) implements JobPolicy {
		public AuditPolicy {
			validateCommon(retention, batchSize, maxBatchesPerRun, fixedDelay, initialDelay);
			if (securityRetention == null || securityRetention.isZero() || securityRetention.isNegative()) {
				throw new IllegalArgumentException("app.cleanup.audit-logs.security-retention must be a positive ISO-8601 duration");
			}
			if (securityRetention.compareTo(retention) < 0) {
				throw new IllegalArgumentException("app.cleanup.audit-logs.security-retention must not be shorter than retention");
			}
			if (securityActions == null || securityActions.isEmpty()) {
				throw new IllegalArgumentException("app.cleanup.audit-logs.security-actions must not be empty");
			}
			TreeSet<String> normalizedActions = new TreeSet<>();
			for (String action : securityActions) {
				if (action == null || action.isBlank()) {
					throw new IllegalArgumentException("app.cleanup.audit-logs.security-actions must not contain blank values");
				}
				normalizedActions.add(action.trim().toUpperCase(Locale.ROOT));
			}
			securityActions = Set.copyOf(normalizedActions);
		}
	}

	private static void validateCommon(
			Duration retention,
			int batchSize,
			int maxBatchesPerRun,
			Duration fixedDelay,
			Duration initialDelay) {
		if (retention == null || retention.isZero() || retention.isNegative()) {
			throw new IllegalArgumentException("cleanup retention must be a positive ISO-8601 duration");
		}
		if (batchSize < 1 || batchSize > 10_000) {
			throw new IllegalArgumentException("cleanup batch-size must be between 1 and 10000");
		}
		if (maxBatchesPerRun < 1 || maxBatchesPerRun > 1_000) {
			throw new IllegalArgumentException("cleanup max-batches-per-run must be between 1 and 1000");
		}
		if (fixedDelay == null || fixedDelay.compareTo(Duration.ofMinutes(1)) < 0) {
			throw new IllegalArgumentException("cleanup fixed-delay must be an ISO-8601 duration of at least PT1M");
		}
		if (initialDelay == null || initialDelay.isNegative()) {
			throw new IllegalArgumentException("cleanup initial-delay must be a non-negative ISO-8601 duration");
		}
	}

	private static Set<String> defaultSecurityAuditActions() {
		return Set.of(
				"AUTH_EMAIL_VERIFY",
				"AUTH_LOGIN_FAILED",
				"AUTH_LOGIN_SUCCESS",
				"AUTH_LOGOUT",
				"AUTH_PASSWORD_CHANGE",
				"AUTH_REFRESH_TOKEN",
				"MEDIA_ACCESS_DENIED",
				"SECURITY_ACCESS_DENIED",
				"SYSTEM_CONFIG_UPDATE",
				"USER_BLOCKED",
				"USER_CREATE",
				"USER_DELETE",
				"USER_ROLE_CHANGE",
				"USER_STATUS_CHANGE",
				"USER_UNBLOCKED");
	}
}
