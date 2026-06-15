package main.com.chat.wechat.conversation.service;

import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.dto.TypingEventRequest;
import main.com.chat.wechat.conversation.dto.TypingEventResponse;
import main.com.chat.wechat.realtime.dto.RealtimeEvent;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class TypingService {
	private static final Duration REPEAT_DEBOUNCE = Duration.ofSeconds(2);

	private final ConversationService conversationService;
	private final UserRepository userRepository;
	private final RealtimeEventPublisher realtimeEventPublisher;
	private final ConcurrentMap<String, TypingState> typingStates = new ConcurrentHashMap<>();

	public TypingService(
			ConversationService conversationService,
			UserRepository userRepository,
			RealtimeEventPublisher realtimeEventPublisher) {
		this.conversationService = conversationService;
		this.userRepository = userRepository;
		this.realtimeEventPublisher = realtimeEventPublisher;
	}

	public TypingEventResponse publishTyping(UUID actorUserId, UUID conversationId, TypingEventRequest request) {
		findActiveUser(actorUserId);
		conversationService.findAccessibleConversation(actorUserId, conversationId);
		if (request == null || request.typing() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Typing state is required");
		}

		boolean typing = request.typing();
		Instant now = Instant.now();
		boolean published = shouldPublish(actorUserId, conversationId, typing, now);
		String eventType = typing ? "conversation.typing.started" : "conversation.typing.stopped";
		if (published) {
			List<UUID> recipientIds = conversationService.memberIds(conversationId).stream()
					.filter(memberId -> !memberId.equals(actorUserId))
					.toList();
			realtimeEventPublisher.publishToMembersAfterCommit(
					recipientIds,
					RealtimeEvent.of(eventType, conversationId, null, actorUserId, null, Map.of(
							"conversationId", conversationId,
							"userId", actorUserId,
							"typing", typing)));
		}
		return new TypingEventResponse(conversationId, actorUserId, typing, eventType, now, published);
	}

	private boolean shouldPublish(UUID actorUserId, UUID conversationId, boolean typing, Instant now) {
		String key = conversationId + ":" + actorUserId;
		TypingState previous = typingStates.get(key);
		if (previous != null
				&& previous.typing() == typing
				&& previous.lastPublishedAt().plus(REPEAT_DEBOUNCE).isAfter(now)) {
			return false;
		}
		typingStates.put(key, new TypingState(typing, now));
		return true;
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::active)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User account is not active"));
	}

	private record TypingState(boolean typing, Instant lastPublishedAt) {
	}
}
