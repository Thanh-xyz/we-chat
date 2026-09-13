package main.com.chat.wechat.cleanup.job;

import main.com.chat.wechat.cleanup.CleanupJobExecutor;
import main.com.chat.wechat.cleanup.CleanupProperties;
import main.com.chat.wechat.notification.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NotificationCleanupJob {
	private static final String JOB_NAME = "notifications";

	private final NotificationRepository repository;
	private final CleanupProperties properties;
	private final CleanupJobExecutor executor;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean();

	public NotificationCleanupJob(
			NotificationRepository repository,
			CleanupProperties properties,
			CleanupJobExecutor executor,
			Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.executor = executor;
		this.clock = clock;
	}

	@Scheduled(
			fixedDelayString = "${app.cleanup.notifications.fixed-delay:P1D}",
			initialDelayString = "${app.cleanup.notifications.initial-delay:PT30M}")
	public CleanupJobExecutor.CleanupRunResult runCleanup() {
		CleanupProperties.Policy policy = properties.notifications();
		Instant cutoff = clock.instant().minus(policy.retention());
		return executor.execute(JOB_NAME, running, policy,
				() -> repository.deleteReadOrSoftDeletedBefore(cutoff, cutoff, policy.batchSize()));
	}
}

