# Friendship / Contact API

Base path: `/api/friends`

Policy: direct 1-1 conversations and direct messages require an active friendship and no block in either direction. Group conversations keep the existing membership-based policy.

## Permissions

- `FRIEND_READ`: list friends, requests, blocks, summary, user search.
- `FRIEND_REQUEST_SEND`: send friend requests.
- `FRIEND_REQUEST_RESPOND`: accept, decline, cancel related requests.
- `FRIEND_DELETE`: unfriend.
- `USER_BLOCK`: block/unblock users.

## Friend Requests

Send request:

```http
POST /api/friends/requests
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "receiverId": "22222222-2222-2222-2222-222222222222",
  "message": "Hi, let's connect"
}
```

If the receiver already has a pending request to the actor, this action auto-accepts that reverse request.

List incoming:

```http
GET /api/friends/requests/incoming?limit=50&offset=0
```

List outgoing:

```http
GET /api/friends/requests/outgoing?limit=50&offset=0
```

Accept, decline, cancel:

```http
POST /api/friends/requests/{requestId}/accept
POST /api/friends/requests/{requestId}/decline
POST /api/friends/requests/{requestId}/cancel
```

Accepting creates two `friendships` rows and creates a direct conversation if one does not already exist.

## Friends

```http
GET /api/friends?q=thanh&limit=50&offset=0
DELETE /api/friends/{friendId}
GET /api/friends/summary
```

Unfriend soft-deletes both friendship rows. Existing direct conversation history is kept.

## Blocks

```http
POST /api/friends/block/{userId}
DELETE /api/friends/block/{userId}
GET /api/friends/blocked?limit=50&offset=0
```

```json
{
  "reason": "spam"
}
```

Blocking cancels pending friend requests in both directions, soft-deletes active friendship rows, keeps conversation history, and prevents new direct messages.

## User Search

```http
GET /api/users/search?q=tha&limit=50&offset=0
```

Searches active users by username, display name, and email, but response does not expose email. Users blocked by either side are omitted.

Response item:

```json
{
  "userId": "22222222-2222-2222-2222-222222222222",
  "username": "thanh",
  "displayName": "Thanh",
  "avatarUrl": null,
  "status": "ONLINE",
  "relationStatus": "NONE"
}
```

Possible visible relation statuses: `NONE`, `FRIEND`, `INCOMING_REQUEST`, `OUTGOING_REQUEST`.

## Realtime Events

Published on `/topic/users/{userId}`:

- `friend.request.sent`
- `friend.request.accepted`
- `friend.request.declined`
- `friend.request.cancelled`
- `friend.removed`
- `user.blocked`
- `user.unblocked`

Notifications are also created for request sent and accepted using the existing in-app notification pipeline.

## Audit

Audit actions:

- `FRIEND_REQUEST_SENT`
- `FRIEND_REQUEST_ACCEPTED`
- `FRIEND_REQUEST_DECLINED`
- `FRIEND_REQUEST_CANCELLED`
- `FRIEND_REMOVED`
- `USER_BLOCKED`
- `USER_UNBLOCKED`

Metadata contains `actorUserId`, `targetUserId`, and `requestId` when available. Friend request messages are not written into audit metadata.

## Production Notes

- `friend_requests` has a partial unique index on unordered pending pairs to prevent duplicate pending requests even under race conditions.
- `friendships` stores two rows per relationship for fast contact-list reads on web and mobile.
- Use paging everywhere; service caps limit at 100.
- Keep existing direct conversations on unfriend/block so chat history remains stable.
- A scheduled job can mark stale pending requests as `EXPIRED` when `expires_at < now()`.
