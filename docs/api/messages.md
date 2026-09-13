# Message History Pagination

Set `Authorization: Bearer <accessToken>` for every request. Both endpoints require `MESSAGE_READ` and preserve the existing conversation membership check.

## List Message History

`GET {{baseUrl}}/api/conversations/{{conversationId}}/messages?limit=50`

The first page is the newest messages. Request the next page with the opaque cursor returned by the previous response:

`GET {{baseUrl}}/api/conversations/{{conversationId}}/messages?limit=50&cursor={{nextCursor}}`

Response:

```json
{
  "items": [
    {
      "id": "{{messageId}}",
      "conversationId": "{{conversationId}}",
      "senderId": "{{userId}}",
      "content": "hello",
      "messageType": "TEXT",
      "replyToMessageId": null,
      "editedAt": null,
      "recalledAt": null,
      "edited": false,
      "recalled": false,
      "reactions": [],
      "attachments": [],
      "createdAt": "2026-09-13T05:00:00Z",
      "updatedAt": "2026-09-13T05:00:00Z"
    }
  ],
  "nextCursor": "{{opaqueCursor}}",
  "hasNext": true,
  "limit": 50
}
```

Messages are ordered by `(createdAt desc, id desc)`. The cursor represents the last returned message in that ordering, so a new message inserted between requests does not cause a duplicate or skip. The server reads `limit + 1` rows to calculate `hasNext`; the extra row is never returned.

## Search Message History

`GET {{baseUrl}}/api/conversations/{{conversationId}}/messages/search?q=hello&limit=50`

Continue search results in the same way:

`GET {{baseUrl}}/api/conversations/{{conversationId}}/messages/search?q=hello&limit=50&cursor={{nextCursor}}`

Search keeps the existing filters: soft-deleted, recalled, and current-user-deleted messages are excluded, and the query matches message content or the sender's username, email, or display name.

## Validation and migration notes

- `limit` is required to be between `1` and `100`; invalid values return `400 Bad Request`.
- Omit `cursor` for the first page. An empty page returns `hasNext: false` and `nextCursor: null`.
- Cursors are opaque, versioned Base64URL values. They are for pagination, not encryption; clients must store and replay them without parsing or modifying them.
- Malformed, unsupported, or oversized cursors return `400 Bad Request`; the server never falls back to the first page.
- `offset` is no longer supported for message history or message search and returns `400 Bad Request`. Other APIs that still document `offset` are unchanged.
- Authorization and conversation membership are checked on every request, including requests carrying a cursor.
