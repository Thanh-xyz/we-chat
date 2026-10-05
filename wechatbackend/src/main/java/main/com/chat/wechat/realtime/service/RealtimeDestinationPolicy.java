package main.com.chat.wechat.realtime.service;

import main.com.chat.wechat.realtime.dto.RealtimeEnvelope;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RealtimeDestinationPolicy {
	private static final String USER_QUEUE_SUFFIX = "/queue/conversation-events";
	private static final Pattern INSTANCE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
	private static final Pattern USER_QUEUE = Pattern.compile("^/user/([0-9a-fA-F-]{36})/queue/conversation-events$");
	private static final Pattern NOTIFICATION_TOPIC = Pattern.compile("^/topic/users/([0-9a-fA-F-]{36})/notifications$");
	private static final Pattern USER_TOPIC = Pattern.compile("^/topic/users/([0-9a-fA-F-]{36})$");
	private static final Set<String> EVENT_TYPES = Set.of(
			"attachment.deleted",
			"conversation.archived",
			"conversation.member.added",
			"conversation.member.removed",
			"conversation.muted",
			"conversation.pinned",
			"conversation.read",
			"conversation.typing.started",
			"conversation.typing.stopped",
			"conversation.unread.updated",
			"conversation.updated",
			"friend.removed",
			"friend.request.accepted",
			"friend.request.cancelled",
			"friend.request.declined",
			"friend.request.sent",
			"media.created",
			"media.deleted",
			"message.created",
			"message.deleted_for_me",
			"message.edited",
			"message.reaction.added",
			"message.reaction.removed",
			"message.recalled",
			"notification.count_updated",
			"notification.created",
			"notification.deleted",
			"notification.read",
			"notification.read_all",
			"notification.updated",
			"user.blocked",
			"user.unblocked");

	public String userQueue(UUID userId) {
		return "/user/%s%s".formatted(requireUserId(userId), USER_QUEUE_SUFFIX);
	}

	public String notificationTopic(UUID userId) {
		return "/topic/users/%s/notifications".formatted(requireUserId(userId));
	}

	public String userTopic(UUID userId) {
		return "/topic/users/%s".formatted(requireUserId(userId));
	}

	public Destination parse(String destination) {
		Matcher matcher = USER_QUEUE.matcher(destination == null ? "" : destination);
		if (matcher.matches()) {
			return new Destination(Kind.USER_QUEUE, UUID.fromString(matcher.group(1)));
		}
		matcher = NOTIFICATION_TOPIC.matcher(destination == null ? "" : destination);
		if (matcher.matches()) {
			return new Destination(Kind.NOTIFICATION_TOPIC, UUID.fromString(matcher.group(1)));
		}
		matcher = USER_TOPIC.matcher(destination == null ? "" : destination);
		if (matcher.matches()) {
			return new Destination(Kind.USER_TOPIC, UUID.fromString(matcher.group(1)));
		}
		throw new IllegalArgumentException("Unsupported realtime destination");
	}

	public void validate(RealtimeEnvelope envelope) {
		if (envelope == null || envelope.version() != RealtimeEnvelope.CURRENT_VERSION || envelope.eventId() == null
				|| envelope.createdAt() == null || envelope.originInstanceId() == null
				|| !INSTANCE_ID.matcher(envelope.originInstanceId()).matches()
				|| envelope.payload() == null || envelope.payload().isMissingNode() || envelope.payload().isNull()) {
			throw new IllegalArgumentException("Invalid realtime envelope");
		}
		if (!EVENT_TYPES.contains(envelope.eventType())) {
			throw new IllegalArgumentException("Unsupported realtime event type");
		}
		parse(envelope.destination());
	}

	public String category(String destination) {
		return parse(destination).kind().metricName;
	}

	private static String requireUserId(UUID userId) {
		if (userId == null) {
			throw new IllegalArgumentException("Realtime user id is required");
		}
		return userId.toString();
	}

	public enum Kind {
		USER_QUEUE("user_queue"),
		NOTIFICATION_TOPIC("notification_topic"),
		USER_TOPIC("user_topic");

		private final String metricName;

		Kind(String metricName) {
			this.metricName = metricName;
		}
	}

	public record Destination(Kind kind, UUID userId) {
	}
}
