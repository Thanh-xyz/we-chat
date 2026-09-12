# Phase 7 Notification API

Set `Authorization: Bearer <accessToken>` for REST calls.

## REST

### List Notifications

`GET {{baseUrl}}/api/notifications?limit=50&offset=0`

```json
{
  "notifications": [
    {
      "id": "{{notificationId}}",
      "userId": "{{currentUserId}}",
      "actorUserId": "{{actorUserId}}",
      "conversationId": "{{conversationId}}",
      "messageId": "{{messageId}}",
      "type": "MESSAGE",
      "title": "Tin nhắn mới",
      "content": "hello",
      "read": false,
      "createdAt": "2026-06-15T08:00:00Z",
      "readAt": null
    }
  ],
  "unreadCount": 1,
  "limit": 50,
  "offset": 0
}
```

### Unread Count

`GET {{baseUrl}}/api/notifications/unread-count`

```json
{
  "count": 15
}
```

### Mark Read

`POST {{baseUrl}}/api/notifications/{{notificationId}}/read`

Only the owner of the notification can mark it as read.

### Mark All Read

`POST {{baseUrl}}/api/notifications/read-all`

```json
{
  "count": 0
}
```

### Soft Delete

`DELETE {{baseUrl}}/api/notifications/{{notificationId}}`

Only the owner can delete. The row is soft-deleted.

### Preferences

`GET {{baseUrl}}/api/notifications/preferences`

`PUT {{baseUrl}}/api/notifications/preferences`

```json
{
  "messageEnabled": true,
  "mentionEnabled": true,
  "reactionEnabled": true,
  "groupEnabled": true,
  "systemEnabled": true,
  "emailEnabled": false,
  "pushEnabled": false
}
```

Preference updates are audited as `NOTIFICATION_PREFERENCE_UPDATE`.

## WebSocket

Connect to `{{baseUrl}}/ws` with native header:

```text
Authorization: Bearer {{accessToken}}
```

Subscribe:

```text
/topic/users/{{currentUserId}}/notifications
```

Users can only subscribe to their own notification topic.

Realtime event examples:

```json
{
  "eventType": "notification.created",
  "notificationId": "{{notificationId}}",
  "userId": "{{currentUserId}}",
  "unreadCount": 3,
  "payload": {
    "notification": {}
  },
  "occurredAt": "2026-06-15T08:00:00Z"
}
```

Supported event types:

```text
notification.created
notification.updated
notification.deleted
notification.read
notification.read_all
notification.count_updated
```

## Event-Driven Flow

The message and conversation modules only publish `NotificationEvent`. `NotificationService` listens after commit, checks preferences, creates in-app notification rows, writes `notification_delivery` rows for the `IN_APP` channel, updates unread count, and pushes realtime events.

## Postman Test Flow

1. User A, B, C are in conversation C.
2. User A sends a message.
3. User B and C receive `MESSAGE` notifications; User A receives none.
4. User A sends `@userB check giúp mình`.
5. User B receives `MENTION`; other members receive `MESSAGE`.
6. User B disables `mentionEnabled`, then User A mentions B again; B receives no fallback message notification for that mention.
7. User B reacts to User A's message; User A receives `REACTION`.
8. Add User D to a group; D receives `GROUP_INVITE`.
9. Rename or update group avatar; members except actor receive `GROUP_UPDATE`.
10. Call `GET /api/notifications/unread-count`.
11. Mark a notification read; count decreases and `notification.count_updated` is pushed.
12. User B attempts `POST /api/notifications/{{userA_notificationId}}/read`; expected `404`.
