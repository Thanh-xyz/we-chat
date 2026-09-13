package main.com.chat.wechat.cleanup.job;

import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.cleanup.CleanupJobExecutor;
import main.com.chat.wechat.cleanup.CleanupProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ExpiredRefreshTokenCleanupJob {
	private static final String JOB_NAME = "refresh_tokens";

	private final RefreshTokenRepository repository;
	private final CleanupProperties properties;
	private final CleanupJobExecutor executor;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean();

	public ExpiredRefreshTokenCleanupJob(
			RefreshTokenRepository repository,
			CleanupProperties properties,
			CleanupJobExecutor executor,
			Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.executor = executor;
		this.clock = clock;
	}

	@Scheduled(
			fixedDelayString = "${app.cleanup.refresh-tokens.fixed-delay:PT1H}",
			initialDelayString = "${app.cleanup.refresh-tokens.initial-delay:PT5M}")
	public CleanupJobExecutor.CleanupRunResult runCleanup() {
		CleanupProperties.Policy policy = properties.refreshTokens();
		Instant cutoff = clock.instant().minus(policy.retention());
		return executor.execute(JOB_NAME, running, policy,
				() -> repository.deleteExpiredOrRevokedBefore(cutoff, cutoff, policy.batchSize()));
	}
}

