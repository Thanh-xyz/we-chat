package main.com.chat.wechat.cleanup;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

@Component
public class CleanupJobExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CleanupJobExecutor.class);

	private final MeterRegistry meterRegistry;

	public CleanupJobExecutor(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public CleanupRunResult execute(
			String jobName,
			AtomicBoolean running,
			CleanupProperties.JobPolicy policy,
			IntSupplier deleteBatch) {
		if (!policy.enabled()) {
			return CleanupRunResult.disabled();
		}
		if (!running.compareAndSet(false, true)) {
			LOGGER.warn("Cleanup job skipped because a previous execution is still running job={}", jobName);
			counter("cleanup.skipped", jobName).increment();
			return CleanupRunResult.overlapping();
		}

		long startedAt = System.nanoTime();
		int batches = 0;
		int totalDeleted = 0;
		counter("cleanup.runs", jobName).increment();
		try {
			for (int attempt = 0; attempt < policy.maxBatchesPerRun(); attempt++) {
				int deleted = deleteBatch.getAsInt();
				if (deleted < 0 || deleted > policy.batchSize()) {
					throw new IllegalStateException("Cleanup repository returned an invalid deleted-row count");
				}
				if (deleted == 0) {
					break;
				}
				batches++;
				totalDeleted += deleted;
				counter("cleanup.deleted.rows", jobName).increment(deleted);
				if (deleted < policy.batchSize()) {
					break;
				}
			}

			long durationMs = elapsedMillis(startedAt);
			timer(jobName).record(durationMs, TimeUnit.MILLISECONDS);
			LOGGER.info("Cleanup job completed job={} batches={} deleted={} durationMs={}",
					jobName, batches, totalDeleted, durationMs);
			return CleanupRunResult.succeeded(batches, totalDeleted);
		} catch (RuntimeException exception) {
			long durationMs = elapsedMillis(startedAt);
			counter("cleanup.failures", jobName).increment();
			timer(jobName).record(durationMs, TimeUnit.MILLISECONDS);
			LOGGER.warn("Cleanup job failed job={} batches={} deleted={} durationMs={} exceptionClass={}",
					jobName, batches, totalDeleted, durationMs, exception.getClass().getName());
			return CleanupRunResult.failed(batches, totalDeleted);
		} finally {
			running.set(false);
		}
	}

	private Counter counter(String name, String jobName) {
		return Counter.builder(name)
				.description("Data-retention cleanup job counter")
				.tag("job", jobName)
				.register(meterRegistry);
	}

	private Timer timer(String jobName) {
		return Timer.builder("cleanup.duration")
				.description("Data-retention cleanup job duration")
				.tag("job", jobName)
				.register(meterRegistry);
	}

	private long elapsedMillis(long startedAt) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
	}

	public record CleanupRunResult(Status status, int batches, int deletedRows) {
		public enum Status {
			SUCCEEDED,
			FAILED,
			DISABLED,
			OVERLAPPING
		}

		static CleanupRunResult succeeded(int batches, int deletedRows) {
			return new CleanupRunResult(Status.SUCCEEDED, batches, deletedRows);
		}

		static CleanupRunResult failed(int batches, int deletedRows) {
			return new CleanupRunResult(Status.FAILED, batches, deletedRows);
		}

		static CleanupRunResult disabled() {
			return new CleanupRunResult(Status.DISABLED, 0, 0);
		}

		static CleanupRunResult overlapping() {
			return new CleanupRunResult(Status.OVERLAPPING, 0, 0);
		}
	}
}

