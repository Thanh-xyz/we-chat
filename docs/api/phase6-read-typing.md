# Phase 6 Read Receipts, Unread Count, Typing

Set `Authorization: Bearer <accessToken>` for every REST request and WebSocket `CONNECT`.

## REST Endpoints

### Mark Conversation Read

`POST {{baseUrl}}/api/conversations/{{conversationId}}/read`

Body can be empty to mark read through the latest readable message, or specify a target message:

```json
{
  "lastReadMessageId": "{{messageId}}"
}
```

Response:

```json
{
  "conversationId": "{{conversationId}}",
  "userId": "{{userBId}}",
  "lastReadMessageId": "{{messageId}}",
  "lastReadAt": "2026-06-15T05:00:00Z",
  "readAt": "2026-06-15T05:01:00Z",
  "unreadCount": 0
}
```

The message must belong to the conversation, must not be recalled or deleted, and must not be deleted-for-me by the current user.

### Conversation Unread Count

`GET {{baseUrl}}/api/conversations/{{conversationId}}/unread-count`

```json
{
  "conversationId": "{{conversationId}}",
  "unreadCount": 3,
  "totalUnreadCount": null
}
```

### Total Unread Count

`GET {{baseUrl}}/api/conversations/unread-count?includeArchived=false`

```json
{
  "conversationId": null,
  "unreadCount": null,
  "totalUnreadCount": 8
}
```

### Read Receipts

`GET {{baseUrl}}/api/conversations/{{conversationId}}/read-receipts`

Returns active members only:

```json
[
  {
    "conversationId": "{{conversationId}}",
    "userId": "{{userBId}}",
    "lastReadMessageId": "{{messageId}}",
    "lastReadAt": "2026-06-15T05:00:00Z",
    "readAt": "2026-06-15T05:01:00Z"
  }
]
```

### Typing REST Fallback

`POST {{baseUrl}}/api/conversations/{{conversationId}}/typing`

```json
{
  "typing": true
}
```

Use `false` for stopped typing. Typing is not stored in the database and repeated identical events are debounced.

## WebSocket Typing

Connect to `{{baseUrl}}/ws` with native header:

```text
Authorization: Bearer {{accessToken}}
```

Subscribe user-specific events:

```text
/user/queue/conversation-events
```

Send started/stopped typing:

```text
SEND /app/conversations/{{conversationId}}/typing
content-type: application/json

{"typing":true}
```

Expected events to other active conversation members:

```json
{
  "type": "conversation.typing.started",
  "conversationId": "{{conversationId}}",
  "actorUserId": "{{userBId}}",
  "payload": {
    "conversationId": "{{conversationId}}",
    "userId": "{{userBId}}",
    "typing": true
  }
}
```

Clients should clear typing UI after 3-5 seconds if no new started event arrives.

## Realtime Events

When a user marks read, members receive:

```text
conversation.read
```

The actor also receives:

```text
conversation.unread.updated
```

When a new message is created, recalled, or deleted-for-me changes unread state, affected users receive:

```text
conversation.unread.updated
```

## Postman Test Flow

1. User A and User B are active members of conversation C.
2. User A sends a message: `POST /api/conversations/{{conversationId}}/messages`.
3. User B calls `GET /api/conversations` and sees `unreadCount` increase.
4. User B calls `GET /api/conversations/{{conversationId}}/unread-count`.
5. User B calls `POST /api/conversations/{{conversationId}}/read` with an empty body or `lastReadMessageId`.
6. User B sees unread count return to 0.
7. User A receives `conversation.read` on `/user/queue/conversation-events`.
8. User B sends typing over REST or STOMP.
9. User A receives `conversation.typing.started` and later `conversation.typing.stopped`.
10. A user outside conversation C calls read/unread/typing APIs and receives 403.
11. Recall an unread message and verify User B's unread count decreases because recalled messages are excluded.
12. User B deletes an unread message for self and verifies that message is excluded from User B's unread count.
