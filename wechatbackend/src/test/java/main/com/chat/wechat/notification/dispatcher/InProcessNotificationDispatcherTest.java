package main.com.chat.wechat.notification.dispatcher;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import main.com.chat.wechat.notification.event.NotificationEvent;
import main.com.chat.wechat.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InProcessNotificationDispatcherTest {
	private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

	@Test
	void submissionReturnsBeforeTheFanoutTaskRuns() {
		RecordingExecutor executor = new RecordingExecutor();
		NotificationService service = mock(NotificationService.class);
		NotificationDispatcher dispatcher = new InProcessNotificationDispatcher(
				executor, service, new SimpleMeterRegistry());
		NotificationEvent event = messageEvent();

		dispatcher.dispatch(event);

		verifyNoInteractions(service);
		executor.runNext();
		verify(service).handleNotificationEvent(event);
	}

	@Test
	void fanoutFailureIsContainedAndRecorded() {
		RecordingExecutor executor = new RecordingExecutor();
		NotificationService service = mock(NotificationService.class);
		doThrow(new IllegalStateException("database unavailable"))
				.when(service).handleNotificationEvent(any(NotificationEvent.class));
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		NotificationDispatcher dispatcher = new InProcessNotificationDispatcher(executor, service, registry);

		dispatcher.dispatch(messageEvent());
		executor.runNext();

		assertThat(registry.get("notification_dispatch_total")
				.tag("type", "MESSAGE_CREATED")
				.tag("result", "failure")
				.counter()
				.count()).isEqualTo(1);
	}

	@Test
	void queueRejectionDoesNotBubbleIntoTheMessageRequest() {
		NotificationService service = mock(NotificationService.class);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		NotificationDispatcher dispatcher = new InProcessNotificationDispatcher(
				command -> { throw new RejectedExecutionException("queue full"); },
				service,
				registry);

		dispatcher.dispatch(messageEvent());

		verifyNoInteractions(service);
		assertThat(registry.get("notification_dispatch_rejections_total")
				.tag("type", "MESSAGE_CREATED")
				.counter()
				.count()).isEqualTo(1);
	}

	private NotificationEvent messageEvent() {
		return NotificationEvent.messageCreated(ACTOR_ID, CONVERSATION_ID, MESSAGE_ID, "hello");
	}

	private static final class RecordingExecutor implements Executor {
		private final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override
		public void execute(Runnable command) {
			tasks.add(command);
		}

		void runNext() {
			Runnable task = tasks.poll();
			if (task == null) {
				throw new AssertionError("No notification task was submitted");
			}
			task.run();
		}
	}
}
