# Phase 10 - Media Gallery & Attachment Management

Media APIs are additive and do not replace the existing `/api/attachments/**` API.

## Policy

- A user can view, preview, download, search, and count media only inside conversations where they are an active member.
- Direct messages remain governed by the existing Phase 9 friendship/block policy: old media history is not physically deleted when users block or unfriend, but new direct messages are blocked by `MessageService`.
- Gallery/search/statistics include only attachments already attached to visible messages. Recalled messages, deleted messages, deleted-for-me messages, and soft-deleted attachments are excluded.
- `/api/media/{attachmentId}` and `/api/media/{attachmentId}/download` cannot be accessed by guessing IDs because repository queries always join active conversation membership.
- `storage_key` and filesystem paths are never returned.

## Endpoints

All endpoints require `Authorization: Bearer <accessToken>`.

### List Conversation Media

`GET /api/conversations/{conversationId}/media`

Query:

- `type`: optional `IMAGE`, `VIDEO`, `VOICE`, `FILE`
- `from`: optional ISO-8601 timestamp
- `to`: optional ISO-8601 timestamp
- `uploaderId`: optional UUID
- `limit`: optional, default `50`, max `100`
- `offset`: optional, default `0`

Shortcuts:

- `GET /api/conversations/{conversationId}/media/images`
- `GET /api/conversations/{conversationId}/media/files`
- `GET /api/conversations/{conversationId}/media/voices`

### Search Media

`GET /api/conversations/{conversationId}/media/search`

Query:

- `fileName`: partial file name or MIME type match
- `category`: optional `IMAGE`, `VIDEO`, `VOICE`, `FILE`
- `uploaderId`: optional UUID
- `from`, `to`, `limit`, `offset`

### Detail, Preview, Download, Delete

- `GET /api/media/{attachmentId}`
- `GET /api/media/{attachmentId}/preview`
- `GET /api/media/{attachmentId}/download`
- `DELETE /api/media/{attachmentId}`

Delete is uploader-only and soft-deletes the attachment record. Physical storage cleanup can be handled by a retention worker.

### Statistics

`GET /api/conversations/{conversationId}/media/stats`

Returns:

- `totalFiles`
- `totalImages`
- `totalVideos`
- `totalVoices`
- `totalStorageBytes`
- `largestFile`
- `latestUpload`

## Realtime Events

Published to active conversation members through the existing user queue:

- `media.created`
- `media.deleted`

Payload includes:

- `conversationId`
- `attachmentId`
- `category`

## Audit

Actions:

- `MEDIA_UPLOAD`
- `MEDIA_DOWNLOAD`
- `MEDIA_DELETE`
- `MEDIA_ACCESS_DENIED`

Audit metadata stores only safe metadata such as conversation id, message id, category, file name, and size. It does not store binary content, local paths, or storage keys.

## Storage

The media module reads and downloads objects only through `FileStorageService`, keeping compatibility with local storage and future MinIO/S3/Cloudinary implementations.

## Postman Flow

1. Login User A and User B.
2. Create or reuse a conversation containing both users.
3. Upload image with `POST /api/attachments/upload`.
4. Send a message using the uploaded `attachmentId`.
5. Upload file and voice samples, then send messages for them.
6. List media with `/api/conversations/{conversationId}/media`.
7. List images/files/voices with shortcut endpoints.
8. Search by partial file name and MIME type.
9. Download with `/api/media/{attachmentId}/download`; verify `downloadCount` increases.
10. Delete as uploader; verify it disappears from list/search/stats.
11. Try detail/download as a non-member; expect 403/404 and `MEDIA_ACCESS_DENIED` audit.
12. Watch realtime clients for `media.created` and `media.deleted`.
