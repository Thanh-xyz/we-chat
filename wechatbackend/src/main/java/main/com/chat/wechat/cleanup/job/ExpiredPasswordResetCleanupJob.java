package main.com.chat.wechat.cleanup.job;

import main.com.chat.wechat.auth.repository.PasswordResetTokenRepository;
import main.com.chat.wechat.cleanup.CleanupJobExecutor;
import main.com.chat.wechat.cleanup.CleanupProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ExpiredPasswordResetCleanupJob {
	private static final String JOB_NAME = "password_reset_tokens";

	private final PasswordResetTokenRepository repository;
	private final CleanupProperties properties;
	private final CleanupJobExecutor executor;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean();

	public ExpiredPasswordResetCleanupJob(
			PasswordResetTokenRepository repository,
			CleanupProperties properties,
			CleanupJobExecutor executor,
			Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.executor = executor;
		this.clock = clock;
	}

	@Scheduled(
			fixedDelayString = "${app.cleanup.password-reset-tokens.fixed-delay:PT1H}",
			initialDelayString = "${app.cleanup.password-reset-tokens.initial-delay:PT10M}")
	public CleanupJobExecutor.CleanupRunResult runCleanup() {
		CleanupProperties.Policy policy = properties.passwordResetTokens();
		Instant cutoff = clock.instant().minus(policy.retention());
		return executor.execute(JOB_NAME, running, policy,
				() -> repository.deleteExpiredOrUsedBefore(cutoff, cutoff, policy.batchSize()));
	}
}

