package main.com.chat.wechat.cleanup;

import main.com.chat.wechat.audit.repository.AuditLogRepository;
import main.com.chat.wechat.auth.repository.EmailVerificationTokenRepository;
import main.com.chat.wechat.auth.repository.PasswordResetTokenRepository;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupRepositoryTest {
	private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

	private JdbcTemplate jdbc;
	private RefreshTokenRepository refreshTokens;
	private PasswordResetTokenRepository passwordResetTokens;
	private EmailVerificationTokenRepository emailVerificationTokens;
	private NotificationRepository notifications;
	private AuditLogRepository auditLogs;

	@BeforeEach
	void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:cleanup_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
				"sa",
				"");
		jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table refresh_tokens (id uuid primary key, expires_at timestamp with time zone not null, revoked_at timestamp with time zone)");
		jdbc.execute("create table password_reset_tokens (id uuid primary key, expires_at timestamp with time zone not null, used_at timestamp with time zone)");
		jdbc.execute("create table email_verification_tokens (id uuid primary key, expires_at timestamp with time zone not null, used_at timestamp with time zone)");
		jdbc.execute("create table notifications (id uuid primary key, is_read boolean not null, created_at timestamp with time zone not null, read_at timestamp with time zone, deleted_at timestamp with time zone)");
		jdbc.execute("create table audit_logs (id uuid primary key, action varchar(120) not null, created_at timestamp with time zone not null)");

		refreshTokens = new RefreshTokenRepository(jdbc);
		passwordResetTokens = new PasswordResetTokenRepository(jdbc);
		emailVerificationTokens = new EmailVerificationTokenRepository(jdbc);
		notifications = new NotificationRepository(jdbc);
		auditLogs = new AuditLogRepository(jdbc);
	}

	@Test
	void refreshCleanupDeletesOldExpiredAndRevokedRowsButPreservesActiveAndRecentRevocation() {
		Instant cutoff = NOW.minusSeconds(30L * 24 * 60 * 60);
		UUID expired = insertRefresh(cutoff.minusSeconds(1), null);
		UUID revoked = insertRefresh(NOW.plusSeconds(3600), cutoff.minusSeconds(1));
		UUID active = insertRefresh(NOW.plusSeconds(3600), null);
		UUID recentlyRevoked = insertRefresh(NOW.plusSeconds(3600), cutoff.plusSeconds(1));

		assertThat(refreshTokens.deleteExpiredOrRevokedBefore(cutoff, cutoff, 10)).isEqualTo(2);

		assertThat(exists("refresh_tokens", expired)).isFalse();
		assertThat(exists("refresh_tokens", revoked)).isFalse();
		assertThat(exists("refresh_tokens", active)).isTrue();
		assertThat(exists("refresh_tokens", recentlyRevoked)).isTrue();
	}

	@Test
	void refreshCleanupRespectsBatchLimitAndIsIdempotent() {
		Instant cutoff = NOW.minusSeconds(60);
		insertRefresh(cutoff.minusSeconds(1), null);
		insertRefresh(cutoff.minusSeconds(2), null);

		assertThat(refreshTokens.deleteExpiredOrRevokedBefore(cutoff, cutoff, 1)).isEqualTo(1);
		assertThat(count("refresh_tokens")).isEqualTo(1);
		assertThat(refreshTokens.deleteExpiredOrRevokedBefore(cutoff, cutoff, 1)).isEqualTo(1);
		assertThat(refreshTokens.deleteExpiredOrRevokedBefore(cutoff, cutoff, 1)).isZero();
	}

	@Test
	void passwordResetCleanupDeletesExpiredAndUsedRowsButPreservesActiveAndRecentlyUsedRows() {
		Instant cutoff = NOW.minusSeconds(24 * 60 * 60);
		UUID expired = insertReset(cutoff.minusSeconds(1), null);
		UUID used = insertReset(NOW.plusSeconds(3600), cutoff.minusSeconds(1));
		UUID active = insertReset(NOW.plusSeconds(3600), null);
		UUID recentlyUsed = insertReset(NOW.plusSeconds(3600), cutoff.plusSeconds(1));

		assertThat(passwordResetTokens.deleteExpiredOrUsedBefore(cutoff, cutoff, 10)).isEqualTo(2);

		assertThat(exists("password_reset_tokens", expired)).isFalse();
		assertThat(exists("password_reset_tokens", used)).isFalse();
		assertThat(exists("password_reset_tokens", active)).isTrue();
		assertThat(exists("password_reset_tokens", recentlyUsed)).isTrue();
	}

	@Test
	void emailVerificationCleanupDeletesExpiredAndUsedRowsButPreservesActiveAndRecentlyUsedRows() {
		Instant cutoff = NOW.minusSeconds(7L * 24 * 60 * 60);
		UUID expired = insertVerification(cutoff.minusSeconds(1), null);
		UUID used = insertVerification(NOW.plusSeconds(3600), cutoff.minusSeconds(1));
		UUID active = insertVerification(NOW.plusSeconds(3600), null);
		UUID recentlyUsed = insertVerification(NOW.plusSeconds(3600), cutoff.plusSeconds(1));

		assertThat(emailVerificationTokens.deleteExpiredOrUsedBefore(cutoff, cutoff, 10)).isEqualTo(2);

		assertThat(exists("email_verification_tokens", expired)).isFalse();
		assertThat(exists("email_verification_tokens", used)).isFalse();
		assertThat(exists("email_verification_tokens", active)).isTrue();
		assertThat(exists("email_verification_tokens", recentlyUsed)).isTrue();
	}

	@Test
	void notificationCleanupDeletesOnlyOldReadOrSoftDeletedRows() {
		Instant cutoff = NOW.minusSeconds(180L * 24 * 60 * 60);
		UUID oldRead = insertNotification(true, cutoff.minusSeconds(1), null);
		UUID oldSoftDeletedUnread = insertNotification(false, null, cutoff.minusSeconds(1));
		UUID oldUnread = insertNotification(false, null, null);
		UUID recentRead = insertNotification(true, cutoff.plusSeconds(1), null);

		assertThat(notifications.deleteReadOrSoftDeletedBefore(cutoff, cutoff, 10)).isEqualTo(2);

		assertThat(exists("notifications", oldRead)).isFalse();
		assertThat(exists("notifications", oldSoftDeletedUnread)).isFalse();
		assertThat(exists("notifications", oldUnread)).isTrue();
		assertThat(exists("notifications", recentRead)).isTrue();
	}

	@Test
	void auditCleanupAppliesLongerRetentionToSecurityActions() {
		Instant standardCutoff = NOW.minusSeconds(180L * 24 * 60 * 60);
		Instant securityCutoff = NOW.minusSeconds(365L * 24 * 60 * 60);
		UUID oldStandard = insertAudit("MESSAGE_EDIT", standardCutoff.minusSeconds(1));
		UUID oldButRetainedSecurity = insertAudit("AUTH_LOGIN_FAILED", standardCutoff.minusSeconds(1));
		UUID expiredSecurity = insertAudit("AUTH_LOGIN_FAILED", securityCutoff.minusSeconds(1));
		UUID recentStandard = insertAudit("MESSAGE_EDIT", standardCutoff.plusSeconds(1));

		assertThat(auditLogs.deleteBefore(
				standardCutoff,
				securityCutoff,
				Set.of("AUTH_LOGIN_FAILED", "SECURITY_ACCESS_DENIED"),
				10)).isEqualTo(2);

		assertThat(exists("audit_logs", oldStandard)).isFalse();
		assertThat(exists("audit_logs", expiredSecurity)).isFalse();
		assertThat(exists("audit_logs", oldButRetainedSecurity)).isTrue();
		assertThat(exists("audit_logs", recentStandard)).isTrue();
	}

	@Test
	void everyDeleteBatchHasRequiresNewTransactionBoundary() throws Exception {
		assertRequiresNew(RefreshTokenRepository.class, "deleteExpiredOrRevokedBefore", Instant.class, Instant.class, int.class);
		assertRequiresNew(PasswordResetTokenRepository.class, "deleteExpiredOrUsedBefore", Instant.class, Instant.class, int.class);
		assertRequiresNew(EmailVerificationTokenRepository.class, "deleteExpiredOrUsedBefore", Instant.class, Instant.class, int.class);
		assertRequiresNew(NotificationRepository.class, "deleteReadOrSoftDeletedBefore", Instant.class, Instant.class, int.class);
		assertRequiresNew(AuditLogRepository.class, "deleteBefore", Instant.class, Instant.class, Set.class, int.class);
	}

	private UUID insertRefresh(Instant expiresAt, Instant revokedAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into refresh_tokens (id, expires_at, revoked_at) values (?, ?, ?)",
				id, timestamp(expiresAt), timestamp(revokedAt));
		return id;
	}

	private UUID insertReset(Instant expiresAt, Instant usedAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into password_reset_tokens (id, expires_at, used_at) values (?, ?, ?)",
				id, timestamp(expiresAt), timestamp(usedAt));
		return id;
	}

	private UUID insertVerification(Instant expiresAt, Instant usedAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into email_verification_tokens (id, expires_at, used_at) values (?, ?, ?)",
				id, timestamp(expiresAt), timestamp(usedAt));
		return id;
	}

	private UUID insertNotification(boolean read, Instant readAt, Instant deletedAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into notifications (id, is_read, created_at, read_at, deleted_at) values (?, ?, ?, ?, ?)",
				id, read, timestamp(NOW.minusSeconds(400L * 24 * 60 * 60)), timestamp(readAt), timestamp(deletedAt));
		return id;
	}

	private UUID insertAudit(String action, Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into audit_logs (id, action, created_at) values (?, ?, ?)", id, action, timestamp(createdAt));
		return id;
	}

	private boolean exists(String table, UUID id) {
		Integer count = jdbc.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id);
		return count != null && count > 0;
	}

	private int count(String table) {
		Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
		return count == null ? 0 : count;
	}

	private Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private void assertRequiresNew(Class<?> type, String methodName, Class<?>... parameterTypes) throws Exception {
		Method method = type.getMethod(methodName, parameterTypes);
		Transactional transactional = method.getAnnotation(Transactional.class);
		assertThat(transactional).isNotNull();
		assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
	}
}

