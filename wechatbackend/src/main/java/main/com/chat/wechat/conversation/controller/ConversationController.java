package main.com.chat.wechat.conversation.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.conversation.dto.ConversationResponse;
import main.com.chat.wechat.conversation.dto.CreateConversationRequest;
import main.com.chat.wechat.conversation.service.ConversationService;
import main.com.chat.wechat.message.dto.CreateMessageRequest;
import main.com.chat.wechat.message.dto.MessageResponse;
import main.com.chat.wechat.message.service.MessageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return conversationService.list(user.id(), limit, offset);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('CONVERSATION_READ')")
	public ConversationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return conversationService.get(user.id(), id);
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
}
