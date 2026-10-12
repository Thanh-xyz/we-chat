# Attachment API

Production flow uses upload-before-send. Docker/prod uses a private Supabase Storage S3-compatible bucket:

1. Client requests `POST /api/attachments/presigned-upload`.
2. Server validates membership, size, extension and declared MIME type, generates the object key and returns a short-lived PUT URL.
3. Client uploads directly to Supabase Storage, then calls `POST /api/attachments/complete`.
4. Server reads the object, verifies size, magic bytes, checksum and image decoding where applicable, then marks it `READY`.
5. Client sends a message with `attachmentIds`.
6. Backend checks every attachment is READY, owned by the current user, belongs to the same conversation, then attaches it to the message.

The legacy `POST /api/attachments/upload` multipart endpoint remains available for compatibility and uses the same server-side validation. The legacy `attachments` metadata field is still accepted for backward compatibility, but new clients should use `attachmentIds`.

## Configuration

```properties
app.storage.type=s3
app.storage.local-root=uploads
app.storage.endpoint=https://<project-ref>.storage.supabase.co/storage/v1/s3
app.storage.presigned-endpoint=https://<project-ref>.storage.supabase.co/storage/v1/s3
app.storage.bucket=attachments
app.storage.region=<region-from-supabase-dashboard>
app.storage.path-style=true
app.storage.upload-url-ttl=PT10M
app.storage.download-url-ttl=PT5M
app.storage.operation-timeout=PT5S
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

`GET {{baseUrl}}/api/attachments/{{attachmentId}}/download-url`

Returns a short-lived presigned GET URL only after membership authorization. The frontend uses it immediately and does not persist it.

`GET {{baseUrl}}/api/attachments/{{attachmentId}}/download`

Legacy authenticated streaming fallback. A recalled/deleted/non-READY attachment returns `404`.

### Delete Attachment

`DELETE {{baseUrl}}/api/attachments/{{attachmentId}}`

Only the uploader can delete. The metadata becomes inaccessible, the private object is removed or queued for bounded cleanup, and members receive realtime event `attachment.deleted`.

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
