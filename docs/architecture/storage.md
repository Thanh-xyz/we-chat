# Attachment object storage

R16 moves attachment binaries out of each backend container's filesystem. PostgreSQL remains the source of truth for attachment ownership and message relationships; a private S3-compatible bucket is the source of truth for bytes.

```text
Browser ── REST authorization/finalize ──► Backend #1/#2 ──► PostgreSQL
   │                                      │
   └── short-lived presigned PUT/GET ────┴──────────────► Supabase Storage S3 API
```

The backend keeps the `FileStorageService` abstraction. `LocalFileStorageService` remains available for unit tests and explicit local fallback; the S3-compatible adapter uses the AWS SDK for Java v2 without requiring a local MinIO server. Business services never receive a bucket name or SDK object.

## Upload lifecycle

1. `POST /api/attachments/presigned-upload` authenticates the user, checks conversation membership, extension, declared MIME type and the existing per-category size limit.
2. The server generates an opaque UUID-based key under `attachments/quarantine/`, creates DB metadata with `storage_status=INIT`, and returns only the attachment ID, short-lived PUT URL, required headers and expiry.
3. The browser uploads directly to Supabase Storage through a short-lived S3 presigned URL without backend credentials.
4. `POST /api/attachments/complete` performs `HEAD`, size verification, magic-byte detection, SHA-256 calculation and `ImageIO` decoding for images. Only then does the row become `READY` and eligible for a message or download.
5. Invalid or abandoned objects remain inaccessible and are removed by the bounded attachment cleanup job after the configured retention period.

The existing multipart endpoint remains available for compatibility. It performs the same validation before writing the object and creates a `READY` row immediately.

## Download and authorization

`GET /api/attachments/{id}/download-url` first checks active-user and conversation membership, then returns a presigned GET URL with the configured short TTL. The legacy `/download` endpoint remains as an authenticated streaming fallback. Private bucket policy, not object ACLs, is used; application authorization happens before every URL is created.

Presigned URLs are temporary credentials. They are not persisted, logged, or returned as attachment metadata. The frontend uses a URL only for the immediate transfer and never receives Supabase S3 access keys or secret keys.

## Keys and metadata

Keys are generated from server UUIDs and date prefixes. Original filenames are display metadata only and are sanitized before persistence and response headers. No email, username, JWT, password, raw filesystem path or client-supplied object key enters a storage key.

`message_attachments.storage_status` is the lifecycle guard:

```text
INIT → READY
  └→ FAILED → DELETED (cleanup)
READY → DELETE_PENDING → DELETED
```

Only `READY` rows are returned by message/attachment access queries. `scan_status=CLEAN` means the existing content validation completed; R16 does not introduce an antivirus scanner.

## Configuration

```properties
app.storage.type=s3
app.storage.endpoint=https://<project-ref>.storage.supabase.co/storage/v1/s3
app.storage.presigned-endpoint=https://<project-ref>.storage.supabase.co/storage/v1/s3
app.storage.bucket=attachments
app.storage.region=<region-from-supabase-dashboard>
app.storage.upload-url-ttl=PT10M
app.storage.download-url-ttl=PT5M
app.storage.operation-timeout=PT5S
```

`endpoint` is the direct Supabase Storage S3 hostname used for backend and browser presigned requests. Supabase requires SigV4 and path-style addressing for this endpoint; do not rewrite the host after signing.

Object-storage calls use a bounded connect/read/write/call timeout (`PT5S` by default, configurable through `STORAGE_OPERATION_TIMEOUT`) so a storage outage fails fast instead of tying up request threads.

Each backend performs an idempotent bucket-existence check against Supabase and creates the private bucket when needed; a concurrent create is accepted only for the documented already-exists response. Supabase S3 access keys are server-only credentials with broad bucket access, so they must never enter the frontend bundle.

## Delete and retention

Attachment and message-recall deletion first makes metadata inaccessible and marks the object `DELETE_PENDING`, then removes the object. A storage failure is logged with only operation/result/exception class, exported in low-cardinality metrics, and leaves the row observable for cleanup. The R14-style bounded cleanup job removes old `INIT`, `FAILED` and `DELETE_PENDING` objects; it never removes `READY` objects.

This is deliberately not an exactly-once distributed transaction between PostgreSQL and object storage. A deployment must monitor cleanup failures and retain the migration/verification procedure before deleting old local data.

## Migration boundary from existing MinIO

The current deployment still contains MinIO object metadata, so this migration does not delete or detach `minio_data`. Existing MinIO objects are deferred for an explicit copy-and-verify cutover; the old volume is the rollback source until every referenced row has been verified in Supabase. The application must not be considered fully cut over for legacy attachments until this procedure completes:

1. quiesce new attachment writes;
2. enumerate DB rows and MinIO objects without exposing object keys in logs;
3. copy each object to the same bucket/key in Supabase using server-side credentials;
4. verify size, content type and SHA-256 in both stores;
5. verify representative downloads through every backend replica and record the manifest outside application logs;
6. retain MinIO read access for rollback until the manifest is complete;
7. only then stop/archive MinIO and remove its volume in a separately approved change.

The copy is intentionally not part of normal startup and is not run automatically by the application.

## Health and observability

Supabase Storage contributes `objectStorage` to production readiness. It does not affect JVM liveness. Metrics use only fixed labels such as `operation`, `result` and `mediaType`; object keys, attachment IDs, filenames, checksums, URLs and credentials are never labels or log fields. No binary bytes are written to Redis.

No antivirus scanner, object backup/replication or malware scanning is included in R16. Supabase bucket lifecycle, retention and recovery remain deployment responsibilities.
