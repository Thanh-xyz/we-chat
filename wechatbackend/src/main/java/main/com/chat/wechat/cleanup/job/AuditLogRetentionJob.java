package main.com.chat.wechat.cleanup.job;

import main.com.chat.wechat.audit.repository.AuditLogRepository;
import main.com.chat.wechat.cleanup.CleanupJobExecutor;
import main.com.chat.wechat.cleanup.CleanupProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AuditLogRetentionJob {
	private static final String JOB_NAME = "audit_logs";

	private final AuditLogRepository repository;
	private final CleanupProperties properties;
	private final CleanupJobExecutor executor;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean();

	public AuditLogRetentionJob(
			AuditLogRepository repository,
			CleanupProperties properties,
			CleanupJobExecutor executor,
			Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.executor = executor;
		this.clock = clock;
	}

	@Scheduled(
			fixedDelayString = "${app.cleanup.audit-logs.fixed-delay:P1D}",
			initialDelayString = "${app.cleanup.audit-logs.initial-delay:PT45M}")
	public CleanupJobExecutor.CleanupRunResult runCleanup() {
		CleanupProperties.AuditPolicy policy = properties.auditLogs();
		Instant standardCutoff = clock.instant().minus(policy.retention());
		Instant securityCutoff = clock.instant().minus(policy.securityRetention());
		return executor.execute(JOB_NAME, running, policy,
				() -> repository.deleteBefore(
						standardCutoff,
						securityCutoff,
						policy.securityActions(),
						policy.batchSize()));
	}
}
