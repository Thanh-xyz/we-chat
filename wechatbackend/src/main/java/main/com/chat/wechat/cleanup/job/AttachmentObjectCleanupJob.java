package main.com.chat.wechat.cleanup.job;

import main.com.chat.wechat.attachment.storage.FileStorageService;
import main.com.chat.wechat.cleanup.CleanupJobExecutor;
import main.com.chat.wechat.cleanup.CleanupProperties;
import main.com.chat.wechat.message.model.MessageAttachment;
import main.com.chat.wechat.message.repository.MessageAttachmentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AttachmentObjectCleanupJob {
	private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentObjectCleanupJob.class);
	private static final String JOB_NAME = "attachment_objects";

	private final MessageAttachmentRepository repository;
	private final FileStorageService storage;
	private final CleanupProperties properties;
	private final CleanupJobExecutor executor;
	private final Clock clock;
	private final Counter cleanupCounter;
	private final AtomicBoolean running = new AtomicBoolean();

	public AttachmentObjectCleanupJob(
			MessageAttachmentRepository repository,
			FileStorageService storage,
			CleanupProperties properties,
			CleanupJobExecutor executor,
			Clock clock,
			MeterRegistry meterRegistry) {
		this.repository = repository;
		this.storage = storage;
		this.properties = properties;
		this.executor = executor;
		this.clock = clock;
		this.cleanupCounter = Counter.builder("storage_orphan_cleanup_total")
				.description("Attachment objects removed after failed or abandoned lifecycle")
				.register(meterRegistry);
	}

	@Scheduled(
			fixedDelayString = "${app.cleanup.attachments.fixed-delay:PT1H}",
			initialDelayString = "${app.cleanup.attachments.initial-delay:PT20M}")
	public CleanupJobExecutor.CleanupRunResult runCleanup() {
		CleanupProperties.Policy policy = properties.attachments();
		Instant cutoff = clock.instant().minus(policy.retention());
		return executor.execute(JOB_NAME, running, policy, () -> deleteBatch(cutoff, policy.batchSize()));
	}

	private int deleteBatch(Instant cutoff, int batchSize) {
		List<MessageAttachment> candidates = repository.findUnfinalizedBefore(cutoff, batchSize);
		int deleted = 0;
		for (MessageAttachment candidate : candidates) {
			try {
				storage.delete(candidate.storageKey());
				repository.markStorageDeleted(candidate.id(), Instant.now(clock));
				cleanupCounter.increment();
				deleted++;
			} catch (IOException exception) {
				LOGGER.warn("Attachment object cleanup failed exceptionClass={}", exception.getClass().getName());
			}
		}
		return deleted;
	}
}
