package main.com.chat.wechat.message.controller;

import jakarta.validation.Valid;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import main.com.chat.wechat.message.dto.EditMessageRequest;
import main.com.chat.wechat.message.dto.MessageReactionResponse;
import main.com.chat.wechat.message.dto.MessageResponse;
import main.com.chat.wechat.message.dto.ReactionRequest;
import main.com.chat.wechat.message.service.MessageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
	private final MessageService messageService;

	public MessageController(MessageService messageService) {
		this.messageService = messageService;
	}

	@PatchMapping("/{messageId}")
	@PreAuthorize("hasAuthority('MESSAGE_EDIT')")
	public MessageResponse edit(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId,
			@Valid @RequestBody EditMessageRequest request) {
		return messageService.edit(user.id(), messageId, request);
	}

	@PostMapping("/{messageId}/recall")
	@PreAuthorize("hasAuthority('MESSAGE_DELETE')")
	public MessageResponse recall(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID messageId) {
		return messageService.recall(user.id(), messageId);
	}

	@DeleteMapping("/{messageId}")
	@PreAuthorize("hasAuthority('MESSAGE_DELETE')")
	public void deleteForMe(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID messageId) {
		messageService.deleteForMe(user.id(), messageId);
	}

	@PostMapping("/{messageId}/reactions")
	@PreAuthorize("hasAuthority('MESSAGE_REACT')")
	public List<MessageReactionResponse> addReaction(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId,
			@Valid @RequestBody ReactionRequest request) {
		return messageService.addReaction(user.id(), messageId, request);
	}

	@DeleteMapping("/{messageId}/reactions")
	@PreAuthorize("hasAuthority('MESSAGE_REACT')")
	public List<MessageReactionResponse> deleteReaction(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId,
			@Valid @RequestBody ReactionRequest request) {
		return messageService.deleteReaction(user.id(), messageId, request);
	}
}
