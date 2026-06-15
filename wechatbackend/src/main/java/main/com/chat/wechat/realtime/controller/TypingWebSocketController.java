package main.com.chat.wechat.realtime.controller;

import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.dto.TypingEventRequest;
import main.com.chat.wechat.conversation.service.TypingService;
import main.com.chat.wechat.realtime.security.WebSocketUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class TypingWebSocketController {
	private final TypingService typingService;

	public TypingWebSocketController(TypingService typingService) {
		this.typingService = typingService;
	}

	@MessageMapping("/conversations/{conversationId}/typing")
	public void typing(
			Principal principal,
			@DestinationVariable UUID conversationId,
			@Payload TypingEventRequest request) {
		if (!(principal instanceof WebSocketUserPrincipal userPrincipal)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "WebSocket authentication required");
		}
		typingService.publishTyping(userPrincipal.userId(), conversationId, request);
	}
}
