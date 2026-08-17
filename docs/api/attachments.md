# Attachment API

Production flow uses upload-before-send:

1. Client uploads a real file to `POST /api/attachments/upload`.
2. Server validates membership, size, extension and MIME type, stores the file, and returns `attachmentId`.
3. Client sends a message with `attachmentIds`.
4. Backend checks every attachment is pending, uploaded by the current user, belongs to the same conversation, then attaches it to the message.

This is preferred over sending attachment metadata directly in `CreateMessageRequest` because the server owns storage keys, checksum, MIME validation and access control before the message becomes visible. The legacy `attachments` metadata field is still accepted for backward compatibility, but new clients should use `attachmentIds`.

## Configuration

```properties
app.storage.type=local
app.storage.local-root=uploads
app.attachment.image-max-size=10MB
app.attachment.file-max-size=50MB
app.attachment.voice-max-size=25MB
app.attachment.allowed-image-types=image/jpeg,image/png,image/webp,image/gif
app.attachment.allowed-file-types=application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation,text/plain,application/zip,application/x-zip-compressed
app.attachment.allowed-voice-types=audio/mpeg,audio/wav,audio/x-wav,audio/mp4,audio/webm,audio/x-m4a
```

## Postman Examples

Set `Authorization: Bearer <accessToken>` for every request.

### Upload Image

`POST {{baseUrl}}/api/attachments/upload?conversationId={{conversationId}}&fileType=IMAGE`

Body: `form-data`

```text
file = choose image.jpg/png/webp/gif
```

The form-data key must be exactly `file` in lowercase. `File` or other names will be treated as a missing upload part.

Expected `200`:

```json
{
  "id": "attachment-uuid",
  "conversationId": "conversation-uuid",
  "uploaderId": "user-uuid",
  "originalFileName": "photo.png",
  "fileUrl": "/api/attachments/attachment-uuid/download",
  "mimeType": "image/png",
  "fileType": "IMAGE",
  "fileSize": 12345,
  "checksum": "sha256",
  "scanStatus": "CLEAN",
  "createdAt": "2026-06-12T08:00:00Z"
}
```

### Upload File

`POST {{baseUrl}}/api/attachments/upload?conversationId={{conversationId}}&fileType=FILE`

Use a supported `pdf/doc/docx/xls/xlsx/ppt/pptx/txt/zip` file under 50MB.

### Upload Voice

`POST {{baseUrl}}/api/attachments/upload?conversationId={{conversationId}}&fileType=VOICE`

Use a supported `mp3/wav/m4a/webm` file under 25MB.

### Send Message With Attachment IDs

`POST {{baseUrl}}/api/conversations/{{conversationId}}/messages`

```json
{
  "content": null,
  "messageType": "IMAGE",
  "attachmentIds": ["{{attachmentId}}"]
}
```

For `FILE` and `VOICE`, set `messageType` accordingly. The response `attachments` array contains metadata and the private download URL.

### Get Metadata

`GET {{baseUrl}}/api/attachments/{{attachmentId}}`

Returns attachment metadata only if the current user is still an active member of the conversation.

### Download Attachment

`GET {{baseUrl}}/api/attachments/{{attachmentId}}/download`

Returns the file stream with `Content-Disposition: attachment`. A recalled/deleted attachment returns `404`.

### Delete Attachment

`DELETE {{baseUrl}}/api/attachments/{{attachmentId}}`

Only the uploader can delete. The metadata is soft-deleted, the local file is removed, and members receive realtime event `attachment.deleted`.

### Access Denied Test

1. Login as user A, upload an attachment in conversation C.
2. Login as user B who is not an active member of C.
3. Call `GET {{baseUrl}}/api/attachments/{{attachmentId}}/download`.
4. Expected result: `404` or `403` depending on whether the backend can distinguish missing resource from permission failure in that path; no file content is returned.

### Unsupported Type Test

Upload `exe`, oversized file, or an image with invalid content:

Expected statuses:

```text
400 invalid file/request
413 file too large
415 unsupported media type
```
