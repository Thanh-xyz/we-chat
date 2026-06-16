package main.com.chat.wechat.notification.event;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record NotificationEvent(
		String eventType,
		UUID actorUserId,
		UUID conversationId,
		UUID messageId,
		Set<UUID> recipientUserIds,
		UUID targetUserId,
		String content,
		String emoji,
		Instant occurredAt) {

	public static NotificationEvent messageCreated(UUID actorUserId, UUID conversationId, UUID messageId, String content) {
		return new NotificationEvent("MESSAGE_CREATED", actorUserId, conversationId, messageId, null, null, content, null, Instant.now());
	}

	public static NotificationEvent messageReaction(UUID actorUserId, UUID conversationId, UUID messageId, UUID targetUserId, String emoji) {
		return new NotificationEvent("MESSAGE_REACTION", actorUserId, conversationId, messageId, null, targetUserId, null, emoji, Instant.now());
	}

	public static NotificationEvent groupCreated(UUID actorUserId, UUID conversationId, Set<UUID> recipientUserIds, String content) {
		return new NotificationEvent("GROUP_CREATED", actorUserId, conversationId, null, recipientUserIds, null, content, null, Instant.now());
	}

	public static NotificationEvent groupMemberAdded(UUID actorUserId, UUID conversationId, Set<UUID> recipientUserIds) {
		return new NotificationEvent("GROUP_MEMBER_ADDED", actorUserId, conversationId, null, recipientUserIds, null, null, null, Instant.now());
	}

	public static NotificationEvent groupMemberRemoved(UUID actorUserId, UUID conversationId, UUID targetUserId) {
		return new NotificationEvent("GROUP_MEMBER_REMOVED", actorUserId, conversationId, null, Set.of(targetUserId), targetUserId, null, null, Instant.now());
	}

	public static NotificationEvent groupNameChanged(UUID actorUserId, UUID conversationId, Set<UUID> recipientUserIds, String content) {
		return new NotificationEvent("GROUP_NAME_CHANGED", actorUserId, conversationId, null, recipientUserIds, null, content, null, Instant.now());
	}

	public static NotificationEvent groupAvatarChanged(UUID actorUserId, UUID conversationId, Set<UUID> recipientUserIds) {
		return new NotificationEvent("GROUP_AVATAR_CHANGED", actorUserId, conversationId, null, recipientUserIds, null, null, null, Instant.now());
	}

	public static NotificationEvent systemAnnouncement(Set<UUID> recipientUserIds, String content) {
		return new NotificationEvent("SYSTEM_ANNOUNCEMENT", null, null, null, recipientUserIds, null, content, null, Instant.now());
	}
}
