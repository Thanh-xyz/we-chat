package main.com.chat.wechat.notification.service;

import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.message.model.Message;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.notification.dto.NotificationCountResponse;
import main.com.chat.wechat.notification.dto.NotificationPreferenceResponse;
import main.com.chat.wechat.notification.dto.NotificationRealtimeEvent;
import main.com.chat.wechat.notification.dto.NotificationResponse;
import main.com.chat.wechat.notification.dto.NotificationSummaryResponse;
import main.com.chat.wechat.notification.dto.UpdateNotificationPreferenceRequest;
import main.com.chat.wechat.notification.event.NotificationEvent;
import main.com.chat.wechat.notification.model.Notification;
import main.com.chat.wechat.notification.model.NotificationDelivery;
import main.com.chat.wechat.notification.model.NotificationPreference;
import main.com.chat.wechat.notification.repository.NotificationRepository;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationService {
	private static final int MAX_CONTENT_LENGTH = 500;
	private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_\\-]{3,64})");

	private final NotificationRepository notificationRepository;
	private final ConversationMemberRepository conversationMemberRepository;
	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final RealtimeEventPublisher realtimeEventPublisher;
	private final AuditLogService auditLogService;
	private final AuditJsonWriter auditJsonWriter;

	public NotificationService(
			NotificationRepository notificationRepository,
			ConversationMemberRepository conversationMemberRepository,
			MessageRepository messageRepository,
			UserRepository userRepository,
			RealtimeEventPublisher realtimeEventPublisher,
			AuditLogService auditLogService,
			AuditJsonWriter auditJsonWriter) {
		this.notificationRepository = notificationRepository;
		this.conversationMemberRepository = conversationMemberRepository;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
		this.realtimeEventPublisher = realtimeEventPublisher;
		this.auditLogService = auditLogService;
		this.auditJsonWriter = auditJsonWriter;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void handleNotificationEvent(NotificationEvent event) {
		switch (event.eventType()) {
			case "MESSAGE_CREATED" -> handleMessageCreated(event);
			case "MESSAGE_REACTION" -> handleMessageReaction(event);
			case "GROUP_CREATED", "GROUP_MEMBER_ADDED", "GROUP_MEMBER_REMOVED",
					"GROUP_NAME_CHANGED", "GROUP_AVATAR_CHANGED",
					"GROUP_ADMIN_ASSIGNED", "GROUP_OWNER_TRANSFERRED" -> handleGroupEvent(event);
			case "SYSTEM_ANNOUNCEMENT", "USER_BANNED" -> handleSystemEvent(event);
			default -> {
			}
		}
	}

	public NotificationSummaryResponse listNotifications(UUID actorUserId, int limit, int offset) {
		findActiveUser(actorUserId);
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		List<NotificationResponse> notifications = notificationRepository.findByUserId(actorUserId, safeLimit, safeOffset)
				.stream()
				.map(NotificationResponse::from)
				.toList();
		return new NotificationSummaryResponse(notifications, notificationRepository.countUnread(actorUserId), safeLimit, safeOffset);
	}

	public NotificationCountResponse countUnread(UUID actorUserId) {
		findActiveUser(actorUserId);
		return new NotificationCountResponse(notificationRepository.countUnread(actorUserId));
	}

	@Transactional
	public NotificationResponse markRead(UUID actorUserId, UUID notificationId) {
		findActiveUser(actorUserId);
		Notification notification = notificationRepository.markRead(notificationId, actorUserId, Instant.now())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
		int unreadCount = notificationRepository.countUnread(actorUserId);
		publishNotificationEvent(actorUserId, "notification.updated", notification.id(), unreadCount, Map.of(
				"notification", NotificationResponse.from(notification)));
		publishNotificationEvent(actorUserId, "notification.read", notification.id(), unreadCount, Map.of(
				"notificationId", notification.id()));
		publishCountUpdated(actorUserId, unreadCount);
		return NotificationResponse.from(notification);
	}

	@Transactional
	public NotificationCountResponse markAllRead(UUID actorUserId) {
		findActiveUser(actorUserId);
		notificationRepository.markAllRead(actorUserId, Instant.now());
		publishNotificationEvent(actorUserId, "notification.read_all", null, 0, Map.of("count", 0));
		publishCountUpdated(actorUserId, 0);
		return new NotificationCountResponse(0);
	}

	@Transactional
	public void deleteNotification(UUID actorUserId, UUID notificationId) {
		findActiveUser(actorUserId);
		Notification notification = notificationRepository.softDelete(notificationId, actorUserId, Instant.now())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
		int unreadCount = notificationRepository.countUnread(actorUserId);
		publishNotificationEvent(actorUserId, "notification.deleted", notification.id(), unreadCount, Map.of(
				"notificationId", notification.id()));
		publishCountUpdated(actorUserId, unreadCount);
	}

	public NotificationPreferenceResponse getPreference(UUID actorUserId) {
		findActiveUser(actorUserId);
		return NotificationPreferenceResponse.from(preferenceOrCreate(actorUserId));
	}

	@Transactional
	public NotificationPreferenceResponse updatePreference(UUID actorUserId, UpdateNotificationPreferenceRequest request) {
		findActiveUser(actorUserId);
		UpdateNotificationPreferenceRequest safeRequest = request == null
				? new UpdateNotificationPreferenceRequest(null, null, null, null, null, null, null)
				: request;
		NotificationPreference before = preferenceOrCreate(actorUserId);
		Instant now = Instant.now();
		NotificationPreference updated = notificationRepository.updatePreference(new NotificationPreference(
				before.id(),
				actorUserId,
				coalesce(safeRequest.messageEnabled(), before.messageEnabled()),
				coalesce(safeRequest.mentionEnabled(), before.mentionEnabled()),
				coalesce(safeRequest.reactionEnabled(), before.reactionEnabled()),
				coalesce(safeRequest.groupEnabled(), before.groupEnabled()),
				coalesce(safeRequest.systemEnabled(), before.systemEnabled()),
				coalesce(safeRequest.emailEnabled(), before.emailEnabled()),
				coalesce(safeRequest.pushEnabled(), before.pushEnabled()),
				before.createdAt(),
				now));
		auditLogService.log(
				"NOTIFICATION_PREFERENCE_UPDATE",
				"NOTIFICATION_PREFERENCE",
				actorUserId.toString(),
				auditJsonWriter.write(NotificationPreferenceResponse.from(before)),
				auditJsonWriter.write(NotificationPreferenceResponse.from(updated)));
		return NotificationPreferenceResponse.from(updated);
	}

	@Transactional
	public Optional<NotificationResponse> createNotification(
			UUID userId,
			UUID actorUserId,
			UUID conversationId,
			UUID messageId,
			String type,
			String title,
			String content) {
		findActiveUser(userId);
		NotificationPreference preference = preferenceOrDefault(userId);
		if (!enabledForType(preference, type)) {
			return Optional.empty();
		}
		Instant now = Instant.now();
		Notification notification = new Notification(
				UUID.randomUUID(),
				userId,
				actorUserId,
				conversationId,
				messageId,
				type,
				normalizeTitle(title),
				normalizeContent(content),
				false,
				now,
				null,
				null);
		saveAndPublish(List.of(notification), now);
		return Optional.of(NotificationResponse.from(notification));
	}

	private void handleMessageCreated(NotificationEvent event) {
		List<UUID> recipients = conversationMemberRepository.findMemberIds(event.conversationId()).stream()
				.filter(userId -> !userId.equals(event.actorUserId()))
				.toList();
		if (recipients.isEmpty()) {
			return;
		}
		Set<UUID> mentionedUserIds = mentionedMemberIds(event.content(), recipients);
		List<Notification> notifications = new ArrayList<>();
		Instant now = Instant.now();
		Map<UUID, NotificationPreference> preferences = preferencesFor(recipients, now);
		for (UUID recipientId : recipients) {
			boolean mentioned = mentionedUserIds.contains(recipientId);
			String type = mentioned ? "MENTION" : "MESSAGE";
			NotificationPreference preference = preferences.get(recipientId);
			if (!enabledForType(preference, type)) {
				continue;
			}
			notifications.add(new Notification(
					UUID.randomUUID(),
					recipientId,
					event.actorUserId(),
					event.conversationId(),
					event.messageId(),
					type,
					mentioned ? "Bạn được nhắc đến" : "Tin nhắn mới",
					mentioned ? normalizeContent(event.content()) : messagePreview(event.content()),
					false,
					now,
					null,
					null));
		}
		saveAndPublish(notifications, now);
	}

	private void handleMessageReaction(NotificationEvent event) {
		UUID targetUserId = event.targetUserId();
		if (targetUserId == null) {
			targetUserId = messageRepository.findById(event.messageId())
					.map(Message::senderId)
					.orElse(null);
		}
		if (targetUserId == null || targetUserId.equals(event.actorUserId())) {
			return;
		}
		createFromEvent(
				targetUserId,
				event.actorUserId(),
				event.conversationId(),
				event.messageId(),
				"REACTION",
				"Cảm xúc mới",
				"Tin nhắn của bạn có cảm xúc mới" + (StringUtils.hasText(event.emoji()) ? ": " + event.emoji() : ""));
	}

	private void handleGroupEvent(NotificationEvent event) {
		List<UUID> recipients = recipientsForGroupEvent(event);
		if (recipients.isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		Map<UUID, NotificationPreference> preferences = preferencesFor(recipients, now);
		List<Notification> notifications = new ArrayList<>();
		for (UUID recipientId : recipients) {
			if (!enabledForType(preferences.get(recipientId), groupNotificationType(event))) {
				continue;
			}
			notifications.add(new Notification(
					UUID.randomUUID(),
					recipientId,
					event.actorUserId(),
					event.conversationId(),
					event.messageId(),
					groupNotificationType(event),
					groupTitle(event),
					groupContent(event),
					false,
					now,
					null,
					null));
		}
		saveAndPublish(notifications, now);
	}

	private void handleSystemEvent(NotificationEvent event) {
		if (event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		List<UUID> recipients = event.recipientUserIds().stream().toList();
		Map<UUID, NotificationPreference> preferences = preferencesFor(recipients, now);
		List<Notification> notifications = new ArrayList<>();
		for (UUID recipientId : recipients) {
			if (!enabledForType(preferences.get(recipientId), "SYSTEM")) {
				continue;
			}
			notifications.add(new Notification(
					UUID.randomUUID(),
					recipientId,
					event.actorUserId(),
					event.conversationId(),
					event.messageId(),
					"SYSTEM",
					"Thông báo hệ thống",
					normalizeContent(event.content()),
					false,
					now,
					null,
					null));
		}
		saveAndPublish(notifications, now);
	}

	private void createFromEvent(
			UUID userId,
			UUID actorUserId,
			UUID conversationId,
			UUID messageId,
			String type,
			String title,
			String content) {
		NotificationPreference preference = preferenceOrDefault(userId);
		if (!enabledForType(preference, type)) {
			return;
		}
		Instant now = Instant.now();
		saveAndPublish(List.of(new Notification(
				UUID.randomUUID(),
				userId,
				actorUserId,
				conversationId,
				messageId,
				type,
				title,
				normalizeContent(content),
				false,
				now,
				null,
				null)), now);
	}

	private void saveAndPublish(List<Notification> notifications, Instant now) {
		if (notifications == null || notifications.isEmpty()) {
			return;
		}
		notificationRepository.saveAll(notifications);
		notificationRepository.saveDeliveries(notifications.stream()
				.map(notification -> new NotificationDelivery(
						UUID.randomUUID(),
						notification.id(),
						"IN_APP",
						"SENT",
						now,
						now))
				.toList());
		Map<UUID, Integer> unreadCounts = notificationRepository.countUnreadByUserIds(notifications.stream()
				.map(Notification::userId)
				.distinct()
				.toList());
		for (Notification notification : notifications) {
			int unreadCount = unreadCounts.getOrDefault(notification.userId(), 0);
			publishNotificationEvent(
					notification.userId(),
					"notification.created",
					notification.id(),
					unreadCount,
					Map.of("notification", NotificationResponse.from(notification)));
		}
		unreadCounts.forEach(this::publishCountUpdated);
	}

	private Set<UUID> mentionedMemberIds(String content, List<UUID> recipientIds) {
		if (!StringUtils.hasText(content) || recipientIds.isEmpty()) {
			return Set.of();
		}
		Set<UUID> recipientSet = new LinkedHashSet<>(recipientIds);
		Set<UUID> mentionedIds = new LinkedHashSet<>();
		Set<String> usernames = new LinkedHashSet<>();
		Matcher matcher = MENTION_PATTERN.matcher(content);
		while (matcher.find()) {
			String token = matcher.group(1);
			try {
				mentionedIds.add(UUID.fromString(token));
			} catch (IllegalArgumentException exception) {
				usernames.add(token.toLowerCase(Locale.ROOT));
			}
		}
		if (!usernames.isEmpty()) {
			userRepository.findActiveByUsernames(usernames.stream().toList()).stream()
					.map(User::id)
					.forEach(mentionedIds::add);
		}
		mentionedIds.retainAll(recipientSet);
		return mentionedIds;
	}

	private List<UUID> recipientsForGroupEvent(NotificationEvent event) {
		if (event.recipientUserIds() != null && !event.recipientUserIds().isEmpty()) {
			return event.recipientUserIds().stream()
					.filter(userId -> !userId.equals(event.actorUserId()))
					.toList();
		}
		if (event.conversationId() == null) {
			return List.of();
		}
		return conversationMemberRepository.findMemberIds(event.conversationId()).stream()
				.filter(userId -> !userId.equals(event.actorUserId()))
				.toList();
	}

	private Map<UUID, NotificationPreference> preferencesFor(List<UUID> userIds, Instant now) {
		Map<UUID, NotificationPreference> existing = notificationRepository.findPreferencesByUserIds(userIds);
		Map<UUID, NotificationPreference> result = new LinkedHashMap<>();
		for (UUID userId : userIds) {
			result.put(userId, existing.getOrDefault(userId, notificationRepository.defaultPreference(userId, now)));
		}
		return result;
	}

	private NotificationPreference preferenceOrCreate(UUID userId) {
		return notificationRepository.findPreferenceByUserId(userId)
				.orElseGet(() -> notificationRepository.saveDefaultPreference(userId, Instant.now()));
	}

	private NotificationPreference preferenceOrDefault(UUID userId) {
		return notificationRepository.findPreferenceByUserId(userId)
				.orElseGet(() -> notificationRepository.defaultPreference(userId, Instant.now()));
	}

	private boolean enabledForType(NotificationPreference preference, String type) {
		return switch (type) {
			case "MESSAGE" -> preference.messageEnabled();
			case "MENTION" -> preference.mentionEnabled();
			case "REACTION" -> preference.reactionEnabled();
			case "GROUP_INVITE", "GROUP_UPDATE" -> preference.groupEnabled();
			case "SYSTEM" -> preference.systemEnabled();
			default -> false;
		};
	}

	private String groupNotificationType(NotificationEvent event) {
		return switch (event.eventType()) {
			case "GROUP_CREATED", "GROUP_MEMBER_ADDED" -> "GROUP_INVITE";
			default -> "GROUP_UPDATE";
		};
	}

	private String groupTitle(NotificationEvent event) {
		return switch (event.eventType()) {
			case "GROUP_CREATED" -> "Nhóm mới";
			case "GROUP_MEMBER_ADDED" -> "Bạn được thêm vào nhóm";
			case "GROUP_MEMBER_REMOVED" -> "Cập nhật thành viên nhóm";
			case "GROUP_NAME_CHANGED" -> "Tên nhóm đã thay đổi";
			case "GROUP_AVATAR_CHANGED" -> "Ảnh nhóm đã thay đổi";
			case "GROUP_ADMIN_ASSIGNED" -> "Quyền quản trị nhóm đã thay đổi";
			case "GROUP_OWNER_TRANSFERRED" -> "Chủ sở hữu nhóm đã thay đổi";
			default -> "Cập nhật nhóm";
		};
	}

	private String groupContent(NotificationEvent event) {
		if (StringUtils.hasText(event.content())) {
			return normalizeContent(event.content());
		}
		return switch (event.eventType()) {
			case "GROUP_CREATED" -> "Bạn có cuộc trò chuyện nhóm mới";
			case "GROUP_MEMBER_ADDED" -> "Bạn được thêm vào một cuộc trò chuyện nhóm";
			case "GROUP_MEMBER_REMOVED" -> "Thành viên nhóm đã thay đổi";
			case "GROUP_AVATAR_CHANGED" -> "Ảnh đại diện nhóm đã được cập nhật";
			default -> "Cuộc trò chuyện nhóm đã được cập nhật";
		};
	}

	private String messagePreview(String content) {
		return StringUtils.hasText(content) ? normalizeContent(content) : "Bạn có tin nhắn mới";
	}

	private String normalizeTitle(String title) {
		if (!StringUtils.hasText(title)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Notification title is required");
		}
		String normalized = title.trim();
		return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
	}

	private String normalizeContent(String content) {
		if (!StringUtils.hasText(content)) {
			return null;
		}
		String normalized = content.trim();
		return normalized.length() > MAX_CONTENT_LENGTH ? normalized.substring(0, MAX_CONTENT_LENGTH) : normalized;
	}

	private boolean coalesce(Boolean value, boolean fallback) {
		return value == null ? fallback : value;
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User account is not active"));
	}

	private void publishNotificationEvent(
			UUID userId,
			String eventType,
			UUID notificationId,
			Integer unreadCount,
			Map<String, Object> payload) {
		realtimeEventPublisher.publishNotificationToUserAfterCommit(
				userId,
				NotificationRealtimeEvent.of(eventType, notificationId, userId, unreadCount, payload));
	}

	private void publishCountUpdated(UUID userId, int unreadCount) {
		publishNotificationEvent(userId, "notification.count_updated", null, unreadCount, Map.of("count", unreadCount));
	}
}
