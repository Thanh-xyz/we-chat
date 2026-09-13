package main.com.chat.wechat.cleanup;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CleanupPropertiesTest {
	@Test
	void defaultsArePositiveAndConservative() {
		CleanupProperties properties = new CleanupProperties(null, null, null, null, null);

		assertThat(properties.refreshTokens().retention()).isEqualTo(Duration.ofDays(30));
		assertThat(properties.passwordResetTokens().retention()).isEqualTo(Duration.ofDays(1));
		assertThat(properties.emailVerificationTokens().retention()).isEqualTo(Duration.ofDays(7));
		assertThat(properties.notifications().retention()).isEqualTo(Duration.ofDays(180));
		assertThat(properties.auditLogs().retention()).isEqualTo(Duration.ofDays(180));
		assertThat(properties.auditLogs().securityRetention()).isEqualTo(Duration.ofDays(365));
		assertThat(properties.auditLogs().securityActions())
				.contains("AUTH_LOGIN_FAILED", "AUTH_REFRESH_TOKEN", "SECURITY_ACCESS_DENIED", "USER_ROLE_CHANGE");
	}

	@Test
	void policyRejectsUnsafeDurationsAndBatchBounds() {
		assertThatIllegalArgumentException().isThrownBy(() -> policy(Duration.ZERO, 500, 20));
		assertThatIllegalArgumentException().isThrownBy(() -> policy(Duration.ofDays(-1), 500, 20));
		assertThatIllegalArgumentException().isThrownBy(() -> policy(Duration.ofDays(1), 0, 20));
		assertThatIllegalArgumentException().isThrownBy(() -> policy(Duration.ofDays(1), 10_001, 20));
		assertThatIllegalArgumentException().isThrownBy(() -> policy(Duration.ofDays(1), 500, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new CleanupProperties.Policy(
				true,
				Duration.ofDays(1),
				500,
				20,
				Duration.ofSeconds(59),
				Duration.ZERO));
	}

	@Test
	void auditSecurityRetentionCannotBeShorterOrUnclassified() {
		assertThatIllegalArgumentException().isThrownBy(() -> new CleanupProperties.AuditPolicy(
				true,
				Duration.ofDays(365),
				Duration.ofDays(180),
				Set.of("AUTH_LOGIN_FAILED"),
				500,
				20,
				Duration.ofDays(1),
				Duration.ZERO));
		assertThatIllegalArgumentException().isThrownBy(() -> new CleanupProperties.AuditPolicy(
				true,
				Duration.ofDays(180),
				Duration.ofDays(365),
				Set.of(),
				500,
				20,
				Duration.ofDays(1),
				Duration.ZERO));
	}

	private CleanupProperties.Policy policy(Duration retention, int batchSize, int maxBatches) {
		return new CleanupProperties.Policy(
				true,
				retention,
				batchSize,
				maxBatches,
				Duration.ofHours(1),
				Duration.ZERO);
	}
}
