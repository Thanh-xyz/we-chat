package main.com.chat.wechat.conversation.model;

import java.util.Objects;
import java.util.UUID;

public record DirectConversationPair(UUID userLowId, UUID userHighId) {
	public static final String SELF_CONVERSATION_MESSAGE = "Cannot create direct conversation with yourself";

	public static DirectConversationPair of(UUID firstUserId, UUID secondUserId) {
    Objects.requireNonNull(firstUserId, "firstUserId must not be null");
    Objects.requireNonNull(secondUserId, "secondUserId must not be null");

    int comparison = firstUserId.toString().compareTo(secondUserId.toString());

    if (comparison == 0) {
        throw new IllegalArgumentException(SELF_CONVERSATION_MESSAGE);
    }

    return comparison < 0
            ? new DirectConversationPair(firstUserId, secondUserId)
            : new DirectConversationPair(secondUserId, firstUserId);
}
}
