package main.com.chat.wechat.conversation.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.conversation.dto.AddMembersRequest;
import main.com.chat.wechat.conversation.dto.ConversationResponse;
import main.com.chat.wechat.conversation.dto.CreateConversationRequest;
import main.com.chat.wechat.conversation.dto.MuteConversationRequest;
import main.com.chat.wechat.conversation.dto.ReadConversationRequest;
import main.com.chat.wechat.conversation.dto.ReadConversationResponse;
import main.com.chat.wechat.conversation.dto.UpdateConversationRequest;
import main.com.chat.wechat.conversation.service.ConversationService;
import main.com.chat.wechat.message.dto.CreateMessageRequest;
import main.com.chat.wechat.message.dto.MessageResponse;
import main.com.chat.wechat.message.service.MessageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
	private final ConversationService conversationService;
	private final MessageService messageService;

	public ConversationController(ConversationService conversationService, MessageService messageService) {
		this.conversationService = conversationService;
		this.messageService = messageService;
	}

	@PostMapping
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse create(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CreateConversationRequest request) {
		return conversationService.create(user.id(), request);
	}

	@GetMapping
	@PreAuthorize("hasAuthority('CONVERSATION_READ')")
	public List<ConversationResponse> list(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "false") boolean includeArchived,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return conversationService.list(user.id(), includeArchived, limit, offset);
	}

	@GetMapping("/search")
	@PreAuthorize("hasAuthority('CONVERSATION_READ')")
	public List<ConversationResponse> search(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam String q,
			@RequestParam(defaultValue = "false") boolean includeArchived,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return conversationService.search(user.id(), q, includeArchived, limit, offset);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('CONVERSATION_READ')")
	public ConversationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return conversationService.get(user.id(), id);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse updateGroup(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateConversationRequest request) {
		return conversationService.updateGroup(user.id(), id, request);
	}

	@PostMapping("/{id}/members")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse addMembers(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@Valid @RequestBody AddMembersRequest request) {
		return conversationService.addMembers(user.id(), id, request);
	}

	@DeleteMapping("/{id}/members/{userId}")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse removeMember(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@PathVariable UUID userId) {
		return conversationService.removeMember(user.id(), id, userId);
	}

	@PostMapping("/{id}/leave")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse leaveGroup(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return conversationService.leaveGroup(user.id(), id);
	}

	@PostMapping("/{id}/read")
	@PreAuthorize("hasAuthority('CONVERSATION_READ')")
	public ReadConversationResponse markRead(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody(required = false) ReadConversationRequest request) {
		return conversationService.markRead(user.id(), id, request);
	}

	@PostMapping("/{id}/pin")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse pin(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return conversationService.pin(user.id(), id);
	}

	@DeleteMapping("/{id}/pin")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse unpin(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return conversationService.unpin(user.id(), id);
	}

	@PostMapping("/{id}/mute")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse mute(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody(required = false) MuteConversationRequest request) {
		return conversationService.mute(user.id(), id, request);
	}

	@DeleteMapping("/{id}/mute")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse unmute(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return conversationService.unmute(user.id(), id);
	}

	@PostMapping("/{id}/archive")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return conversationService.archive(user.id(), id);
	}

	@DeleteMapping("/{id}/archive")
	@PreAuthorize("hasAuthority('CONVERSATION_WRITE')")
	public ConversationResponse unarchive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return conversationService.unarchive(user.id(), id);
	}

	@PostMapping("/{id}/messages")
	@PreAuthorize("hasAuthority('MESSAGE_SEND')")
	public MessageResponse sendMessage(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@Valid @RequestBody CreateMessageRequest request) {
		return messageService.send(user.id(), id, request);
	}

	@GetMapping("/{id}/messages")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public List<MessageResponse> listMessages(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return messageService.list(user.id(), id, limit, offset);
	}

	@GetMapping("/{id}/messages/search")
	@PreAuthorize("hasAuthority('MESSAGE_READ')")
	public List<MessageResponse> searchMessages(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestParam String q,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return messageService.search(user.id(), id, q, limit, offset);
	}
}
