package main.com.chat.wechat.cleanup.job;

import main.com.chat.wechat.auth.repository.EmailVerificationTokenRepository;
import main.com.chat.wechat.cleanup.CleanupJobExecutor;
import main.com.chat.wechat.cleanup.CleanupProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ExpiredEmailVerificationCleanupJob {
	private static final String JOB_NAME = "email_verification_tokens";

	private final EmailVerificationTokenRepository repository;
	private final CleanupProperties properties;
	private final CleanupJobExecutor executor;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean();

	public ExpiredEmailVerificationCleanupJob(
			EmailVerificationTokenRepository repository,
			CleanupProperties properties,
			CleanupJobExecutor executor,
			Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.executor = executor;
		this.clock = clock;
	}

	@Scheduled(
			fixedDelayString = "${app.cleanup.email-verification-tokens.fixed-delay:PT1H}",
			initialDelayString = "${app.cleanup.email-verification-tokens.initial-delay:PT15M}")
	public CleanupJobExecutor.CleanupRunResult runCleanup() {
		CleanupProperties.Policy policy = properties.emailVerificationTokens();
		Instant cutoff = clock.instant().minus(policy.retention());
		return executor.execute(JOB_NAME, running, policy,
				() -> repository.deleteExpiredOrUsedBefore(cutoff, cutoff, policy.batchSize()));
	}
}

