package main.com.chat.wechat.conversation.service;

import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.dto.AddMembersRequest;
import main.com.chat.wechat.conversation.dto.ConversationResponse;
import main.com.chat.wechat.conversation.dto.CreateConversationRequest;
import main.com.chat.wechat.conversation.dto.MuteConversationRequest;
import main.com.chat.wechat.conversation.dto.ReadConversationRequest;
import main.com.chat.wechat.conversation.dto.ReadConversationResponse;
import main.com.chat.wechat.conversation.dto.UpdateConversationRequest;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.model.ConversationMember;
import main.com.chat.wechat.conversation.model.DirectConversationPair;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.notification.event.NotificationEvent;
import main.com.chat.wechat.notification.event.NotificationEventPublisher;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ConversationService {
	private final ConversationRepository conversationRepository;
	private final ConversationMemberRepository conversationMemberRepository;
	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final AuditLogService auditLogService;
	private final AuditJsonWriter auditJsonWriter;
	private final RealtimeEventPublisher realtimeEventPublisher;
	private final NotificationEventPublisher notificationEventPublisher;

	public ConversationService(
			ConversationRepository conversationRepository,
			ConversationMemberRepository conversationMemberRepository,
			MessageRepository messageRepository,
			UserRepository userRepository,
			AuditLogService auditLogService,
			AuditJsonWriter auditJsonWriter,
			RealtimeEventPublisher realtimeEventPublisher,
			NotificationEventPublisher notificationEventPublisher) {
		this.conversationRepository = conversationRepository;
		this.conversationMemberRepository = conversationMemberRepository;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
		this.auditJsonWriter = auditJsonWriter;
		this.realtimeEventPublisher = realtimeEventPublisher;
		this.notificationEventPublisher = notificationEventPublisher;
	}

	@Transactional
	public ConversationResponse create(UUID actorUserId, CreateConversationRequest request) {
		User actor = findActiveUser(actorUserId);
		Set<UUID> requestedMemberIds = new LinkedHashSet<>(request.memberIds());

		if ("DIRECT".equals(request.type())) {
			if (requestedMemberIds.contains(actorUserId)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, DirectConversationPair.SELF_CONVERSATION_MESSAGE);
			}
			if (requestedMemberIds.size() != 1) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Direct conversations require exactly one other member");
			}
			UUID otherUserId = requestedMemberIds.iterator().next();
			findActiveUser(otherUserId);
			DirectConversationPair pair = normalizeDirectConversationPair(actorUserId, otherUserId);
			return conversationRepository.findDirectByPair(pair.userLowId(), pair.userHighId())
					.map(conversation -> toResponse(actorUserId, conversation))
					.orElseGet(() -> createNewConversation(actor.id(), request, Set.of(actorUserId, otherUserId), pair));
		}

		requestedMemberIds.remove(actorUserId);
		if (requestedMemberIds.size() < 2) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Group conversations require at least two other members");
		}
		Set<UUID> memberIds = new LinkedHashSet<>(requestedMemberIds);
		memberIds.add(actorUserId);
		for (UUID memberId : memberIds) {
			findActiveUser(memberId);
		}
		return createNewConversation(actor.id(), request, memberIds, null);
	}

	@Transactional
	public ConversationResponse updateGroup(UUID actorUserId, UUID conversationId, UpdateConversationRequest request) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		requireGroup(conversation);
		requireGroupManager(conversation.id(), actorUserId);
		String name = StringUtils.hasText(request.name()) ? request.name().trim() : null;
		String avatarUrl = StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl().trim() : null;
		if (name == null && avatarUrl == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "At least one field must be provided");
		}
		Conversation updated = conversationRepository.updateGroup(conversation.id(), name, avatarUrl, Instant.now())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Group conversation not found"));
		auditLogService.log(
				"CONVERSATION_GROUP_UPDATE",
				"CONVERSATION",
				conversation.id().toString(),
				auditJsonWriter.write(new ConversationUpdateAuditValue(conversation.name(), conversation.avatarUrl())),
				auditJsonWriter.write(new ConversationUpdateAuditValue(updated.name(), updated.avatarUrl())));
		publishConversationEvent(updated.id(), RealtimeEvent.of(
				"conversation.updated",
				updated.id(),
				null,
				actorUserId,
				null,
				Map.of("conversationId", updated.id())));
		Set<UUID> recipientIds = new LinkedHashSet<>(memberIds(updated.id()));
		if (name != null && !name.equals(conversation.name())) {
			notificationEventPublisher.publish(NotificationEvent.groupNameChanged(
					actorUserId,
					updated.id(),
					recipientIds,
					"Tên nhóm đã được đổi thành " + updated.name()));
		}
		if (avatarUrl != null && !avatarUrl.equals(conversation.avatarUrl())) {
			notificationEventPublisher.publish(NotificationEvent.groupAvatarChanged(actorUserId, updated.id(), recipientIds));
		}
		return toResponse(actorUserId, updated);
	}

	@Transactional
	public ConversationResponse addMembers(UUID actorUserId, UUID conversationId, AddMembersRequest request) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		requireGroup(conversation);
		requireGroupManager(conversation.id(), actorUserId);
		Set<UUID> userIds = new LinkedHashSet<>(request.userIds());
		userIds.remove(actorUserId);
		if (userIds.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "At least one new member is required");
		}
		Instant now = Instant.now();
		for (UUID userId : userIds) {
			findActiveUser(userId);
			if (conversationMemberRepository.isMember(conversation.id(), userId)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "User is already a member: " + userId);
			}
			conversationMemberRepository.addOrReactivate(new ConversationMember(
					conversation.id(),
					userId,
					"MEMBER",
					null,
					now,
					null,
					null,
					null,
					null,
					null,
					null,
					false));
		}
		auditLogService.log(
				"CONVERSATION_MEMBER_ADD",
				"CONVERSATION",
				conversation.id().toString(),
				null,
				auditJsonWriter.write(new MemberIdsAuditValue(userIds)));
		publishConversationEvent(conversation.id(), RealtimeEvent.of(
				"conversation.member.added",
				conversation.id(),
				null,
				actorUserId,
				null,
				Map.of("userIds", userIds.toString())));
		notificationEventPublisher.publish(NotificationEvent.groupMemberAdded(actorUserId, conversation.id(), userIds));
		return toResponse(actorUserId, conversation);
	}

	@Transactional
	public ConversationResponse removeMember(UUID actorUserId, UUID conversationId, UUID userId) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		requireGroup(conversation);
		ConversationMember actorMember = requireActiveMember(conversation.id(), actorUserId);
		ConversationMember targetMember = requireActiveMember(conversation.id(), userId);
		boolean selfRemove = actorUserId.equals(userId);
		boolean manager = isGroupManager(actorMember);
		if (!selfRemove && !manager) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only group owners or admins can remove members");
		}
		if (selfRemove && "OWNER".equals(targetMember.memberRole())
				&& conversationMemberRepository.activeMemberCount(conversation.id()) > 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Owner cannot leave while the group still has active members");
		}
		if (!selfRemove && "OWNER".equals(targetMember.memberRole())
				&& conversationMemberRepository.activeOwnerCount(conversation.id()) <= 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot remove the last active owner");
		}
		conversationMemberRepository.markLeft(conversation.id(), userId, Instant.now());
		auditLogService.log(
				selfRemove ? "CONVERSATION_MEMBER_LEAVE" : "CONVERSATION_MEMBER_REMOVE",
				"CONVERSATION",
				conversation.id().toString(),
				auditJsonWriter.write(new UserIdAuditValue(userId)),
				null);
		publishConversationEvent(conversation.id(), RealtimeEvent.of(
				"conversation.member.removed",
				conversation.id(),
				null,
				actorUserId,
				userId,
				Map.of("userId", userId)));
		notificationEventPublisher.publish(NotificationEvent.groupMemberRemoved(actorUserId, conversation.id(), userId));
		return toResponse(actorUserId, conversation);
	}

	@Transactional
	public ConversationResponse leaveGroup(UUID actorUserId, UUID conversationId) {
		return removeMember(actorUserId, conversationId, actorUserId);
	}

	@Transactional
	public ReadConversationResponse markRead(UUID actorUserId, UUID conversationId, ReadConversationRequest request) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		UUID lastReadMessageId = request == null ? null : request.lastReadMessageId();
		if (lastReadMessageId == null) {
			lastReadMessageId = conversation.lastMessageId();
		}
		if (lastReadMessageId != null && !messageRepository.existsAnyInConversation(lastReadMessageId, conversation.id())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Read target message is not in this conversation");
		}
		Instant readAt = Instant.now();
		conversationMemberRepository.updateRead(conversation.id(), actorUserId, lastReadMessageId, readAt);
		int unreadCount = messageRepository.countUnread(conversation.id(), actorUserId, lastReadMessageId);
		publishConversationEvent(conversation.id(), RealtimeEvent.of(
				"conversation.read",
				conversation.id(),
				lastReadMessageId,
				actorUserId,
				actorUserId,
				Map.of("lastReadMessageId", lastReadMessageId == null ? "" : lastReadMessageId.toString())));
		return new ReadConversationResponse(conversation.id(), actorUserId, lastReadMessageId, readAt, readAt, unreadCount);
	}

	@Transactional
	public ConversationResponse pin(UUID actorUserId, UUID conversationId) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		ConversationMember member = conversationMemberRepository.updatePinnedAt(conversation.id(), actorUserId, Instant.now());
		auditLogService.logSuccess(
				"CONVERSATION_UPDATE",
				"CONVERSATION",
				conversation.id().toString(),
				null,
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "PIN", true)));
		realtimeEventPublisher.publishToUserAfterCommit(actorUserId, RealtimeEvent.of(
				"conversation.pinned",
				conversation.id(),
				null,
				actorUserId,
				actorUserId,
				Map.of("pinned", true)));
		return toResponse(actorUserId, conversation, member);
	}

	@Transactional
	public ConversationResponse unpin(UUID actorUserId, UUID conversationId) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		ConversationMember member = conversationMemberRepository.updatePinnedAt(conversation.id(), actorUserId, null);
		auditLogService.logSuccess(
				"CONVERSATION_UPDATE",
				"CONVERSATION",
				conversation.id().toString(),
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "PIN", true)),
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "PIN", false)));
		realtimeEventPublisher.publishToUserAfterCommit(actorUserId, RealtimeEvent.of(
				"conversation.pinned",
				conversation.id(),
				null,
				actorUserId,
				actorUserId,
				Map.of("pinned", false)));
		return toResponse(actorUserId, conversation, member);
	}

	@Transactional
	public ConversationResponse mute(UUID actorUserId, UUID conversationId, MuteConversationRequest request) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		Instant mutedUntil = request == null ? null : request.mutedUntil();
		ConversationMember member = conversationMemberRepository.updateMute(conversation.id(), actorUserId, true, mutedUntil);
		auditLogService.logSuccess(
				"CONVERSATION_UPDATE",
				"CONVERSATION",
				conversation.id().toString(),
				null,
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "MUTE", true)));
		realtimeEventPublisher.publishToUserAfterCommit(actorUserId, RealtimeEvent.of(
				"conversation.muted",
				conversation.id(),
				null,
				actorUserId,
				actorUserId,
				Map.of("muted", true, "mutedUntil", mutedUntil == null ? "" : mutedUntil.toString())));
		return toResponse(actorUserId, conversation, member);
	}

	@Transactional
	public ConversationResponse unmute(UUID actorUserId, UUID conversationId) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		ConversationMember member = conversationMemberRepository.updateMute(conversation.id(), actorUserId, false, null);
		auditLogService.logSuccess(
				"CONVERSATION_UPDATE",
				"CONVERSATION",
				conversation.id().toString(),
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "MUTE", true)),
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "MUTE", false)));
		realtimeEventPublisher.publishToUserAfterCommit(actorUserId, RealtimeEvent.of(
				"conversation.muted",
				conversation.id(),
				null,
				actorUserId,
				actorUserId,
				Map.of("muted", false)));
		return toResponse(actorUserId, conversation, member);
	}

	@Transactional
	public ConversationResponse archive(UUID actorUserId, UUID conversationId) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		ConversationMember member = conversationMemberRepository.updateArchivedAt(conversation.id(), actorUserId, Instant.now());
		auditLogService.logSuccess(
				"CONVERSATION_UPDATE",
				"CONVERSATION",
				conversation.id().toString(),
				null,
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "ARCHIVE", true)));
		realtimeEventPublisher.publishToUserAfterCommit(actorUserId, RealtimeEvent.of(
				"conversation.archived",
				conversation.id(),
				null,
				actorUserId,
				actorUserId,
				Map.of("archived", true)));
		return toResponse(actorUserId, conversation, member);
	}

	@Transactional
	public ConversationResponse unarchive(UUID actorUserId, UUID conversationId) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		ConversationMember member = conversationMemberRepository.updateArchivedAt(conversation.id(), actorUserId, null);
		auditLogService.logSuccess(
				"CONVERSATION_UPDATE",
				"CONVERSATION",
				conversation.id().toString(),
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "ARCHIVE", true)),
				auditJsonWriter.write(new MemberPreferenceAuditValue(actorUserId, "ARCHIVE", false)));
		realtimeEventPublisher.publishToUserAfterCommit(actorUserId, RealtimeEvent.of(
				"conversation.archived",
				conversation.id(),
				null,
				actorUserId,
				actorUserId,
				Map.of("archived", false)));
		return toResponse(actorUserId, conversation, member);
	}

	public List<ConversationResponse> list(UUID actorUserId, boolean includeArchived, int limit, int offset) {
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		return toResponses(actorUserId, conversationRepository.findByMember(actorUserId, includeArchived, safeLimit, safeOffset));
	}

	public List<ConversationResponse> search(UUID actorUserId, String query, boolean includeArchived, int limit, int offset) {
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		return toResponses(actorUserId, conversationRepository.searchByMember(actorUserId, query, includeArchived, safeLimit, safeOffset));
	}

	public ConversationResponse get(UUID actorUserId, UUID conversationId) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		return toResponse(actorUserId, conversation);
	}

	public Conversation findAccessibleConversation(UUID actorUserId, UUID conversationId) {
		Conversation conversation = conversationRepository.findById(conversationId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
		if (!conversationMemberRepository.isMember(conversation.id(), actorUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "You are not a member of this conversation");
		}
		return conversation;
	}

	public List<UUID> memberIds(UUID conversationId) {
		return conversationMemberRepository.findMemberIds(conversationId);
	}

	private ConversationResponse createNewConversation(
			UUID actorUserId,
			CreateConversationRequest request,
			Set<UUID> memberIds,
			DirectConversationPair directConversationPair) {
		Instant now = Instant.now();
		Conversation conversation = conversationRepository.save(new Conversation(
				UUID.randomUUID(),
				request.type(),
				normalizeConversationName(request),
				StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl().trim() : null,
				actorUserId,
				null,
				null,
				null,
				now,
				now));
			if ("DIRECT".equals(request.type())) {
				boolean inserted = saveDirectConversationSafely(conversation.id(), directConversationPair);
				if (!inserted) {
					conversationRepository.deleteById(conversation.id());
					return conversationRepository.findDirectByPair(
								directConversationPair.userLowId(),
								directConversationPair.userHighId())
						.map(existingConversation -> toResponse(actorUserId, existingConversation))
						.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Direct conversation already exists"));
			}
		}
		for (UUID memberId : memberIds) {
			conversationMemberRepository.save(new ConversationMember(
					conversation.id(),
					memberId,
					actorUserId.equals(memberId) ? "OWNER" : "MEMBER",
					null,
					now,
					null,
					null,
					null,
					null,
					null,
					null,
					false));
			}
			auditLogService.logSuccess(
					"CONVERSATION_CREATE",
					"CONVERSATION",
					conversation.id().toString(),
					null,
					auditJsonWriter.write(new ConversationCreateAuditValue(conversation.type(), memberIds.size())));
			if ("GROUP".equals(conversation.type())) {
				notificationEventPublisher.publish(NotificationEvent.groupCreated(
						actorUserId,
						conversation.id(),
						new LinkedHashSet<>(memberIds),
						"Bạn được thêm vào nhóm " + conversation.name()));
			}
			return toResponse(actorUserId, conversation);
		}

	private boolean saveDirectConversationSafely(UUID conversationId, DirectConversationPair directConversationPair) {
		try {
			return conversationRepository.saveDirectConversation(
					conversationId,
					directConversationPair.userLowId(),
					directConversationPair.userHighId());
		} catch (DuplicateKeyException exception) {
			return false;
		}
	}

	private List<ConversationResponse> toResponses(UUID actorUserId, List<Conversation> conversations) {
		List<UUID> conversationIds = conversations.stream().map(Conversation::id).toList();
		Map<UUID, List<UUID>> memberIdsByConversationId = conversationMemberRepository.findMemberIdsByConversationIds(conversationIds);
		Map<UUID, Integer> unreadCountsByConversationId = messageRepository.countUnreadByConversationIds(actorUserId, conversationIds);
		Map<UUID, ConversationMember> currentMembersByConversationId =
				conversationMemberRepository.findActiveMembersByConversationIds(actorUserId, conversationIds);
		return conversations.stream()
				.map(conversation -> ConversationResponse.from(
						conversation,
						memberIdsByConversationId.getOrDefault(conversation.id(), List.of()),
						unreadCountsByConversationId.getOrDefault(conversation.id(), 0),
						currentMembersByConversationId.get(conversation.id())))
				.toList();
	}

	private ConversationResponse toResponse(UUID actorUserId, Conversation conversation) {
		return toResponse(actorUserId, conversation, conversationMemberRepository.findActiveMember(conversation.id(), actorUserId).orElse(null));
	}

	private ConversationResponse toResponse(UUID actorUserId, Conversation conversation, ConversationMember currentMember) {
		int unreadCount = messageRepository.countUnreadByConversationIds(actorUserId, List.of(conversation.id()))
				.getOrDefault(conversation.id(), 0);
		return ConversationResponse.from(
				conversation,
				conversationMemberRepository.findMemberIds(conversation.id()),
				unreadCount,
				currentMember);
	}

	private String normalizeConversationName(CreateConversationRequest request) {
		if ("GROUP".equals(request.type()) && !StringUtils.hasText(request.name())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Group conversations require a name");
		}
		return StringUtils.hasText(request.name()) ? request.name().trim() : null;
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Active user not found: " + userId));
	}

	private DirectConversationPair normalizeDirectConversationPair(UUID actorUserId, UUID otherUserId) {
		try {
			return DirectConversationPair.of(actorUserId, otherUserId);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	private void requireGroup(Conversation conversation) {
		if (!"GROUP".equals(conversation.type())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "This operation is only supported for group conversations");
		}
	}

	private void requireGroupManager(UUID conversationId, UUID actorUserId) {
		ConversationMember actorMember = requireActiveMember(conversationId, actorUserId);
		if (!isGroupManager(actorMember)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only group owners or admins can manage this conversation");
		}
	}

	private ConversationMember requireActiveMember(UUID conversationId, UUID userId) {
		return conversationMemberRepository.findActiveMember(conversationId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User is not an active conversation member"));
	}

	private boolean isGroupManager(ConversationMember member) {
		return "OWNER".equals(member.memberRole()) || "ADMIN".equals(member.memberRole());
	}

	private void publishConversationEvent(UUID conversationId, RealtimeEvent event) {
		realtimeEventPublisher.publishToMembersAfterCommit(memberIds(conversationId), event);
	}

	private record ConversationUpdateAuditValue(String name, String avatarUrl) {
	}

	private record ConversationCreateAuditValue(String type, int memberCount) {
	}

	private record MemberPreferenceAuditValue(UUID userId, String preference, boolean enabled) {
	}

	private record MemberIdsAuditValue(Set<UUID> userIds) {
	}

	private record UserIdAuditValue(UUID userId) {
	}
}
