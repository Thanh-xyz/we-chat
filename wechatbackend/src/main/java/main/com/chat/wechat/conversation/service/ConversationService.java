package main.com.chat.wechat.conversation.service;

import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.dto.ConversationResponse;
import main.com.chat.wechat.conversation.dto.CreateConversationRequest;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.model.ConversationMember;
import main.com.chat.wechat.conversation.model.DirectConversationPair;
import main.com.chat.wechat.conversation.repository.ConversationMemberRepository;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ConversationService {
	private final ConversationRepository conversationRepository;
	private final ConversationMemberRepository conversationMemberRepository;
	private final UserRepository userRepository;

	public ConversationService(
			ConversationRepository conversationRepository,
			ConversationMemberRepository conversationMemberRepository,
			UserRepository userRepository) {
		this.conversationRepository = conversationRepository;
		this.conversationMemberRepository = conversationMemberRepository;
		this.userRepository = userRepository;
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
					.map(conversation -> ConversationResponse.from(
							conversation,
							conversationMemberRepository.findMemberIds(conversation.id())))
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
			boolean inserted = conversationRepository.saveDirectConversation(
					conversation.id(),
					directConversationPair.userLowId(),
					directConversationPair.userHighId());
			if (!inserted) {
				conversationRepository.deleteById(conversation.id());
				return conversationRepository.findDirectByPair(
								directConversationPair.userLowId(),
								directConversationPair.userHighId())
						.map(existingConversation -> ConversationResponse.from(
								existingConversation,
								conversationMemberRepository.findMemberIds(existingConversation.id())))
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
					false));
		}
		return ConversationResponse.from(conversation, conversationMemberRepository.findMemberIds(conversation.id()));
	}

	public List<ConversationResponse> list(UUID actorUserId, int limit, int offset) {
		int safeLimit = Math.min(Math.max(limit, 1), 100);
		int safeOffset = Math.max(offset, 0);
		return conversationRepository.findByMember(actorUserId, safeLimit, safeOffset).stream()
				.map(conversation -> ConversationResponse.from(
						conversation,
						conversationMemberRepository.findMemberIds(conversation.id())))
				.toList();
	}

	public ConversationResponse get(UUID actorUserId, UUID conversationId) {
		Conversation conversation = findAccessibleConversation(actorUserId, conversationId);
		return ConversationResponse.from(conversation, conversationMemberRepository.findMemberIds(conversation.id()));
	}

	public Conversation findAccessibleConversation(UUID actorUserId, UUID conversationId) {
		Conversation conversation = conversationRepository.findById(conversationId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
		if (!conversationMemberRepository.isMember(conversation.id(), actorUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "You are not a member of this conversation");
		}
		return conversation;
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
}
