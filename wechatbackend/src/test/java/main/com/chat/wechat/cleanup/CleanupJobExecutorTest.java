package main.com.chat.wechat.cleanup;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupJobExecutorTest {
	private static final CleanupProperties.Policy POLICY = new CleanupProperties.Policy(
			true,
			Duration.ofDays(1),
			2,
			3,
			Duration.ofHours(1),
			Duration.ZERO);

	@Test
	void disabledPolicyDoesNotCallRepositoryOrCreateRunMetric() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		CleanupJobExecutor executor = new CleanupJobExecutor(registry);
		AtomicInteger repositoryCalls = new AtomicInteger();
		CleanupProperties.Policy disabled = new CleanupProperties.Policy(
				false,
				Duration.ofDays(1),
				2,
				3,
				Duration.ofHours(1),
				Duration.ZERO);

		CleanupJobExecutor.CleanupRunResult result = executor.execute(
				"refresh_tokens", new AtomicBoolean(), disabled, repositoryCalls::incrementAndGet);

		assertThat(result.status()).isEqualTo(CleanupJobExecutor.CleanupRunResult.Status.DISABLED);
		assertThat(repositoryCalls).hasValue(0);
		assertThat(registry.find("cleanup.runs").counter()).isNull();
	}

	@Test
	void failureIsContainedAndNextExecutionCanRunWithoutLoggingExceptionMessage() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		CleanupJobExecutor executor = new CleanupJobExecutor(registry);
		AtomicInteger calls = new AtomicInteger();
		AtomicBoolean running = new AtomicBoolean();
		Logger logger = (Logger) LoggerFactory.getLogger(CleanupJobExecutor.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			CleanupJobExecutor.CleanupRunResult failed = executor.execute(
					"refresh_tokens",
					running,
					POLICY,
					() -> {
						if (calls.getAndIncrement() == 0) {
							throw new IllegalStateException("raw-token-must-not-be-logged");
						}
						return 0;
					});
			CleanupJobExecutor.CleanupRunResult succeeded = executor.execute(
					"refresh_tokens",
					running,
					POLICY,
					() -> {
						calls.incrementAndGet();
						return 0;
					});

			assertThat(failed.status()).isEqualTo(CleanupJobExecutor.CleanupRunResult.Status.FAILED);
			assertThat(succeeded.status()).isEqualTo(CleanupJobExecutor.CleanupRunResult.Status.SUCCEEDED);
			assertThat(calls).hasValue(2);
			assertThat(registry.get("cleanup.runs").tag("job", "refresh_tokens").counter().count()).isEqualTo(2);
			assertThat(registry.get("cleanup.failures").tag("job", "refresh_tokens").counter().count()).isEqualTo(1);
			assertThat(appender.list)
					.extracting(ILoggingEvent::getFormattedMessage)
					.noneMatch(message -> message.contains("raw-token-must-not-be-logged"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void committedPartialBatchesAreCountedWhenLaterBatchFails() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		CleanupJobExecutor executor = new CleanupJobExecutor(registry);
		AtomicInteger calls = new AtomicInteger();

		CleanupJobExecutor.CleanupRunResult result = executor.execute(
				"notifications",
				new AtomicBoolean(),
				POLICY,
				() -> {
					if (calls.getAndIncrement() == 0) {
						return 2;
					}
					throw new IllegalStateException("database unavailable");
				});

		assertThat(result.status()).isEqualTo(CleanupJobExecutor.CleanupRunResult.Status.FAILED);
		assertThat(result.batches()).isEqualTo(1);
		assertThat(result.deletedRows()).isEqualTo(2);
		assertThat(registry.get("cleanup.deleted.rows").tag("job", "notifications").counter().count()).isEqualTo(2);
	}

	@Test
	void overlappingInvocationIsSkippedWithoutCallingRepositoryTwice() throws Exception {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		CleanupJobExecutor executor = new CleanupJobExecutor(registry);
		AtomicBoolean running = new AtomicBoolean();
		AtomicInteger repositoryCalls = new AtomicInteger();
		CountDownLatch enteredRepository = new CountDownLatch(1);
		CountDownLatch releaseRepository = new CountDownLatch(1);
		ExecutorService thread = Executors.newSingleThreadExecutor();
		try {
			Future<CleanupJobExecutor.CleanupRunResult> first = thread.submit(() -> executor.execute(
					"audit_logs",
					running,
					POLICY,
					() -> {
						repositoryCalls.incrementAndGet();
						enteredRepository.countDown();
						try {
							if (!releaseRepository.await(5, TimeUnit.SECONDS)) {
								throw new IllegalStateException("test synchronization timed out");
							}
						} catch (InterruptedException exception) {
							Thread.currentThread().interrupt();
							throw new IllegalStateException("test interrupted");
						}
						return 0;
					}));

			assertThat(enteredRepository.await(5, TimeUnit.SECONDS)).isTrue();
			CleanupJobExecutor.CleanupRunResult overlap = executor.execute(
					"audit_logs", running, POLICY, repositoryCalls::incrementAndGet);
			releaseRepository.countDown();

			assertThat(overlap.status()).isEqualTo(CleanupJobExecutor.CleanupRunResult.Status.OVERLAPPING);
			assertThat(first.get(5, TimeUnit.SECONDS).status())
					.isEqualTo(CleanupJobExecutor.CleanupRunResult.Status.SUCCEEDED);
			assertThat(repositoryCalls).hasValue(1);
			assertThat(registry.get("cleanup.skipped").tag("job", "audit_logs").counter().count()).isEqualTo(1);
		} finally {
			releaseRepository.countDown();
			thread.shutdownNow();
		}
	}
}
