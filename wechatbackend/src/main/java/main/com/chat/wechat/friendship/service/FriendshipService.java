package main.com.chat.wechat.friendship.service;

import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.model.ConversationMember;
import main.com.chat.wechat.conversation.model.DirectConversationPair;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.friendship.dto.BlockUserRequest;
import main.com.chat.wechat.friendship.dto.BlockUserResponse;
import main.com.chat.wechat.friendship.dto.FriendRequestResponse;
import main.com.chat.wechat.friendship.dto.FriendResponse;
import main.com.chat.wechat.friendship.dto.FriendUserSummary;
import main.com.chat.wechat.friendship.dto.FriendshipSummaryResponse;
import main.com.chat.wechat.friendship.dto.PublicUserSearchResponse;
import main.com.chat.wechat.friendship.dto.SendFriendRequestRequest;
import main.com.chat.wechat.friendship.model.FriendRequest;
import main.com.chat.wechat.friendship.model.FriendRequestStatus;
import main.com.chat.wechat.friendship.model.RelationStatus;
import main.com.chat.wechat.friendship.model.UserBlock;
import main.com.chat.wechat.friendship.repository.FriendRequestRepository;
import main.com.chat.wechat.friendship.repository.FriendshipRepository;
import main.com.chat.wechat.friendship.repository.UserBlockRepository;
import main.com.chat.wechat.notification.service.NotificationService;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FriendshipService {
	private static final int MAX_LIMIT = 100;
	private static final Duration REQUEST_TTL = Duration.ofDays(30);

	private final FriendRequestRepository friendRequestRepository;
	private final FriendshipRepository friendshipRepository;
	private final UserBlockRepository userBlockRepository;
	private final UserRepository userRepository;
	private final ConversationRepository conversationRepository;
	private final ConversationMemberRepository conversationMemberRepository;
	private final RealtimeEventPublisher realtimeEventPublisher;
	private final NotificationService notificationService;
	private final AuditLogService auditLogService;
	private final AuditJsonWriter auditJsonWriter;

	public FriendshipService(
			FriendRequestRepository friendRequestRepository,
			FriendshipRepository friendshipRepository,
			UserBlockRepository userBlockRepository,
			UserRepository userRepository,
			ConversationRepository conversationRepository,
			ConversationMemberRepository conversationMemberRepository,
			RealtimeEventPublisher realtimeEventPublisher,
			NotificationService notificationService,
			AuditLogService auditLogService,
			AuditJsonWriter auditJsonWriter) {
		this.friendRequestRepository = friendRequestRepository;
		this.friendshipRepository = friendshipRepository;
		this.userBlockRepository = userBlockRepository;
		this.userRepository = userRepository;
		this.conversationRepository = conversationRepository;
		this.conversationMemberRepository = conversationMemberRepository;
		this.realtimeEventPublisher = realtimeEventPublisher;
		this.notificationService = notificationService;
		this.auditLogService = auditLogService;
		this.auditJsonWriter = auditJsonWriter;
	}

	@Transactional
	public FriendRequestResponse sendRequest(UUID actorUserId, SendFriendRequestRequest request) {
		User requester = findActiveUser(actorUserId);
		User receiver = findActiveUser(request.receiverId());
		requireDifferentUsers(actorUserId, receiver.id());
		if (friendshipRepository.existsActive(actorUserId, receiver.id())) {
			throw new ApiException(HttpStatus.CONFLICT, "Users are already friends");
		}
		if (userBlockRepository.existsBlockBetween(actorUserId, receiver.id())) {
			auditLogService.logFailure("FRIEND_REQUEST_SENT", "USER", receiver.id().toString(), "Blocked relationship", auditMetadata(actorUserId, receiver.id(), null));
			throw new ApiException(HttpStatus.FORBIDDEN, "Friend request is not allowed between these users");
		}
		FriendRequest pending = friendRequestRepository.findPendingBetween(actorUserId, receiver.id()).orElse(null);
		if (pending != null) {
			if (pending.requesterId().equals(receiver.id()) && pending.receiverId().equals(actorUserId)) {
				return acceptRequest(actorUserId, pending.id());
			}
			throw new ApiException(HttpStatus.CONFLICT, "A pending friend request already exists");
		}
		Instant now = Instant.now();
		FriendRequest friendRequest = friendRequestRepository.save(new FriendRequest(
				UUID.randomUUID(),
				actorUserId,
				receiver.id(),
				FriendRequestStatus.PENDING,
				trimToNull(request.message(), 255),
				null,
				now.plus(REQUEST_TTL),
				now,
				now));
		auditLogService.logSuccess("FRIEND_REQUEST_SENT", "USER", receiver.id().toString(), null, null, auditMetadata(actorUserId, receiver.id(), friendRequest.id()));
		notificationService.createNotification(
				receiver.id(),
				actorUserId,
				null,
				null,
				"SYSTEM",
				"Lời mời kết bạn",
				requester.displayName() + " đã gửi lời mời kết bạn");
		publishFriendEvent(receiver.id(), "friend.request.sent", actorUserId, receiver.id(), friendRequest.id());
		return FriendRequestResponse.from(friendRequest, requester, receiver);
	}

	public List<FriendRequestResponse> incomingRequests(UUID actorUserId, int limit, int offset) {
		findActiveUser(actorUserId);
		return toRequestResponses(friendRequestRepository.findIncoming(actorUserId, safeLimit(limit), safeOffset(offset)));
	}

	public List<FriendRequestResponse> outgoingRequests(UUID actorUserId, int limit, int offset) {
		findActiveUser(actorUserId);
		return toRequestResponses(friendRequestRepository.findOutgoing(actorUserId, safeLimit(limit), safeOffset(offset)));
	}

	@Transactional
	public FriendRequestResponse acceptRequest(UUID actorUserId, UUID requestId) {
		User actor = findActiveUser(actorUserId);
		FriendRequest request = findRequest(requestId);
		if (!request.receiverId().equals(actorUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only the request receiver can accept this friend request");
		}
		requirePendingAndNotExpired(request);
		User requester = findActiveUser(request.requesterId());
		if (userBlockRepository.existsBlockBetween(request.requesterId(), request.receiverId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Friend request is not allowed between blocked users");
		}
		Instant now = Instant.now();
		friendshipRepository.createTwoWay(request.requesterId(), request.receiverId(), now);
		FriendRequest updated = friendRequestRepository.updateStatus(request.id(), FriendRequestStatus.ACCEPTED, now, now);
		ensureDirectConversation(request.requesterId(), request.receiverId(), now);
		auditLogService.logSuccess("FRIEND_REQUEST_ACCEPTED", "USER", requester.id().toString(), null, null, auditMetadata(actorUserId, requester.id(), request.id()));
		notificationService.createNotification(
				requester.id(),
				actorUserId,
				null,
				null,
				"SYSTEM",
				"Lời mời kết bạn được chấp nhận",
				actor.displayName() + " đã chấp nhận lời mời kết bạn");
		publishFriendEvent(requester.id(), "friend.request.accepted", actorUserId, requester.id(), request.id());
		publishFriendEvent(actorUserId, "friend.request.accepted", actorUserId, requester.id(), request.id());
		return FriendRequestResponse.from(updated, requester, actor);
	}

	@Transactional
	public FriendRequestResponse declineRequest(UUID actorUserId, UUID requestId) {
		User actor = findActiveUser(actorUserId);
		FriendRequest request = findRequest(requestId);
		if (!request.receiverId().equals(actorUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only the request receiver can decline this friend request");
		}
		requirePending(request);
		Instant now = Instant.now();
		FriendRequest updated = friendRequestRepository.updateStatus(request.id(), FriendRequestStatus.DECLINED, now, now);
		User requester = findUser(request.requesterId());
		auditLogService.logSuccess("FRIEND_REQUEST_DECLINED", "USER", requester.id().toString(), null, null, auditMetadata(actorUserId, requester.id(), request.id()));
		publishFriendEvent(requester.id(), "friend.request.declined", actorUserId, requester.id(), request.id());
		return FriendRequestResponse.from(updated, requester, actor);
	}

	@Transactional
	public FriendRequestResponse cancelRequest(UUID actorUserId, UUID requestId) {
		User actor = findActiveUser(actorUserId);
		FriendRequest request = findRequest(requestId);
		if (!request.requesterId().equals(actorUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only the request sender can cancel this friend request");
		}
		requirePending(request);
		Instant now = Instant.now();
		FriendRequest updated = friendRequestRepository.updateStatus(request.id(), FriendRequestStatus.CANCELLED, now, now);
		User receiver = findUser(request.receiverId());
		auditLogService.logSuccess("FRIEND_REQUEST_CANCELLED", "USER", receiver.id().toString(), null, null, auditMetadata(actorUserId, receiver.id(), request.id()));
		publishFriendEvent(receiver.id(), "friend.request.cancelled", actorUserId, receiver.id(), request.id());
		return FriendRequestResponse.from(updated, actor, receiver);
	}

	public List<FriendResponse> friends(UUID actorUserId, String query, int limit, int offset) {
		findActiveUser(actorUserId);
		return friendshipRepository.findFriends(actorUserId, query, safeLimit(limit), safeOffset(offset));
	}

	@Transactional
	public void unfriend(UUID actorUserId, UUID friendId) {
		findActiveUser(actorUserId);
		findActiveUser(friendId);
		if (!friendshipRepository.existsActive(actorUserId, friendId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Friendship not found");
		}
		Instant now = Instant.now();
		friendshipRepository.softDeleteTwoWay(actorUserId, friendId, now);
		auditLogService.logSuccess("FRIEND_REMOVED", "USER", friendId.toString(), null, null, auditMetadata(actorUserId, friendId, null));
		publishFriendEvent(actorUserId, "friend.removed", actorUserId, friendId, null);
		publishFriendEvent(friendId, "friend.removed", actorUserId, friendId, null);
	}

	public FriendshipSummaryResponse summary(UUID actorUserId) {
		findActiveUser(actorUserId);
		return new FriendshipSummaryResponse(
				friendshipRepository.countFriends(actorUserId),
				friendRequestRepository.countIncomingPending(actorUserId),
				friendRequestRepository.countOutgoingPending(actorUserId),
				userBlockRepository.countBlocked(actorUserId));
	}

	@Transactional
	public BlockUserResponse block(UUID actorUserId, UUID targetUserId, BlockUserRequest request) {
		findActiveUser(actorUserId);
		findActiveUser(targetUserId);
		requireDifferentUsers(actorUserId, targetUserId);
		Instant now = Instant.now();
		UserBlock block = userBlockRepository.block(actorUserId, targetUserId, trimToNull(request == null ? null : request.reason(), 255), now);
		friendRequestRepository.cancelPendingBetween(actorUserId, targetUserId, now);
		friendshipRepository.softDeleteTwoWay(actorUserId, targetUserId, now);
		auditLogService.logSuccess("USER_BLOCKED", "USER", targetUserId.toString(), null, null, auditMetadata(actorUserId, targetUserId, null));
		publishFriendEvent(actorUserId, "user.blocked", actorUserId, targetUserId, null);
		return BlockUserResponse.from(block);
	}

	@Transactional
	public void unblock(UUID actorUserId, UUID targetUserId) {
		findActiveUser(actorUserId);
		findUser(targetUserId);
		if (!userBlockRepository.unblock(actorUserId, targetUserId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Block relationship not found");
		}
		auditLogService.logSuccess("USER_UNBLOCKED", "USER", targetUserId.toString(), null, null, auditMetadata(actorUserId, targetUserId, null));
		publishFriendEvent(actorUserId, "user.unblocked", actorUserId, targetUserId, null);
	}

	public List<FriendUserSummary> blockedUsers(UUID actorUserId, int limit, int offset) {
		findActiveUser(actorUserId);
		return userBlockRepository.findBlockedUsers(actorUserId, safeLimit(limit), safeOffset(offset));
	}

	public List<PublicUserSearchResponse> searchUsers(UUID actorUserId, String query, int limit, int offset) {
		findActiveUser(actorUserId);
		List<User> users = userRepository.searchActiveUsers(actorUserId, query, safeLimit(limit), safeOffset(offset));
		List<UUID> userIds = users.stream().map(User::id).toList();
		Map<UUID, RelationStatus> statuses = friendshipRepository.relationStatuses(actorUserId, userIds);
		return users.stream()
				.filter(user -> statuses.getOrDefault(user.id(), RelationStatus.NONE) != RelationStatus.BLOCKED_BY_ME)
				.filter(user -> statuses.getOrDefault(user.id(), RelationStatus.NONE) != RelationStatus.BLOCKED_ME)
				.map(user -> PublicUserSearchResponse.from(user, statuses.getOrDefault(user.id(), RelationStatus.NONE)))
				.toList();
	}

	public void requireCanDirectMessage(UUID actorUserId, UUID otherUserId) {
		if (userBlockRepository.existsBlockBetween(actorUserId, otherUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Direct messaging is not allowed between blocked users");
		}
		if (!friendshipRepository.existsActive(actorUserId, otherUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Direct messaging requires an active friendship");
		}
	}

	private void ensureDirectConversation(UUID firstUserId, UUID secondUserId, Instant now) {
		DirectConversationPair pair = DirectConversationPair.of(firstUserId, secondUserId);
		if (conversationRepository.findDirectByPair(pair.userLowId(), pair.userHighId()).isPresent()) {
			return;
		}
		Conversation conversation = conversationRepository.save(new Conversation(
				UUID.randomUUID(),
				"DIRECT",
				null,
				null,
				firstUserId,
				null,
				null,
				null,
				now,
				now));
		if (!conversationRepository.saveDirectConversation(conversation.id(), pair.userLowId(), pair.userHighId())) {
			conversationRepository.deleteById(conversation.id());
			return;
		}
		for (UUID memberId : Set.of(firstUserId, secondUserId)) {
			conversationMemberRepository.save(new ConversationMember(
					conversation.id(),
					memberId,
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
	}

	private List<FriendRequestResponse> toRequestResponses(List<FriendRequest> requests) {
		List<UUID> userIds = requests.stream()
				.flatMap(request -> List.of(request.requesterId(), request.receiverId()).stream())
				.distinct()
				.toList();
		Map<UUID, User> usersById = userRepository.findByIds(userIds);
		return requests.stream()
				.map(request -> FriendRequestResponse.from(
						request,
						usersById.get(request.requesterId()),
						usersById.get(request.receiverId())))
				.toList();
	}

	private FriendRequest findRequest(UUID requestId) {
		return friendRequestRepository.findById(requestId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Friend request not found"));
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User account is not active"));
	}

	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private void requireDifferentUsers(UUID actorUserId, UUID targetUserId) {
		if (actorUserId.equals(targetUserId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Users cannot perform this action on themselves");
		}
	}

	private void requirePendingAndNotExpired(FriendRequest request) {
		requirePending(request);
		if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
			friendRequestRepository.updateStatus(request.id(), FriendRequestStatus.EXPIRED, Instant.now(), Instant.now());
			throw new ApiException(HttpStatus.BAD_REQUEST, "Friend request has expired");
		}
	}

	private void requirePending(FriendRequest request) {
		if (request.status() != FriendRequestStatus.PENDING) {
			throw new ApiException(HttpStatus.CONFLICT, "Friend request is no longer pending");
		}
	}

	private void publishFriendEvent(UUID userId, String eventType, UUID actorUserId, UUID targetUserId, UUID requestId) {
		realtimeEventPublisher.publishUserTopicAfterCommit(userId, RealtimeEvent.of(
				eventType,
				null,
				null,
				actorUserId,
				targetUserId,
				Map.of(
						"actorUserId", actorUserId,
						"targetUserId", targetUserId,
						"requestId", requestId == null ? "" : requestId.toString())));
	}

	private String auditMetadata(UUID actorUserId, UUID targetUserId, UUID requestId) {
		return auditJsonWriter.write(new FriendshipAuditMetadata(actorUserId, targetUserId, requestId));
	}

	private String trimToNull(String value, int maxLength) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String normalized = value.trim();
		return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
	}

	private int safeLimit(int limit) {
		return Math.min(Math.max(limit, 1), MAX_LIMIT);
	}

	private int safeOffset(int offset) {
		return Math.max(offset, 0);
	}

	private record FriendshipAuditMetadata(UUID actorUserId, UUID targetUserId, UUID requestId) {
	}
}
