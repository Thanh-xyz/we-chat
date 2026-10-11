# Attachment object storage

R16 moves attachment binaries out of each backend container's filesystem. PostgreSQL remains the source of truth for attachment ownership and message relationships; a private S3-compatible bucket is the source of truth for bytes.

```text
Browser ── REST authorization/finalize ──► Backend #1/#2 ──► PostgreSQL
   │                                      │
   └── short-lived presigned PUT/GET ────┴──────────────► MinIO/S3
```

The backend keeps the `FileStorageService` abstraction. `LocalFileStorageService` remains available for unit tests and explicit local fallback; `MinioObjectStorageService` is the Docker/production implementation and contains all MinIO SDK types. Business services never receive a bucket name or SDK object.

## Upload lifecycle

1. `POST /api/attachments/presigned-upload` authenticates the user, checks conversation membership, extension, declared MIME type and the existing per-category size limit.
2. The server generates an opaque UUID-based key under `attachments/quarantine/`, creates DB metadata with `storage_status=INIT`, and returns only the attachment ID, short-lived PUT URL, required headers and expiry.
3. The browser uploads directly to MinIO/S3 without backend credentials.
4. `POST /api/attachments/complete` performs `HEAD`, size verification, magic-byte detection, SHA-256 calculation and `ImageIO` decoding for images. Only then does the row become `READY` and eligible for a message or download.
5. Invalid or abandoned objects remain inaccessible and are removed by the bounded attachment cleanup job after the configured retention period.

The existing multipart endpoint remains available for compatibility. It performs the same validation before writing the object and creates a `READY` row immediately.

## Download and authorization

`GET /api/attachments/{id}/download-url` first checks active-user and conversation membership, then returns a presigned GET URL with the configured short TTL. The legacy `/download` endpoint remains as an authenticated streaming fallback. Private bucket policy, not object ACLs, is used; application authorization happens before every URL is created.

Presigned URLs are temporary credentials. They are not persisted, logged, or returned as attachment metadata. The frontend uses a URL only for the immediate transfer and never receives MinIO root credentials, access keys or secret keys.

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
app.storage.type=minio
app.storage.endpoint=http://minio:9000
app.storage.presigned-endpoint=http://localhost:9000
app.storage.bucket=attachments
app.storage.region=us-east-1
app.storage.upload-url-ttl=PT10M
app.storage.download-url-ttl=PT5M
```

`endpoint` is for backend-to-storage traffic. `presigned-endpoint` must be resolvable by browsers; in local Compose only the MinIO API is bound to loopback port `9000`, while the administration console is not published. Production should use a TLS object endpoint or an S3-compatible provider and keep MinIO administration private.

Compose creates the private bucket once through the idempotent `minio-init` service. Backend replicas do not race to create it. The `minio_data` named volume is persistence, not backup; production still needs object backup/replication and a tested recovery procedure.

## Delete and retention

Attachment and message-recall deletion first makes metadata inaccessible and marks the object `DELETE_PENDING`, then removes the object. A storage failure is logged with only operation/result/exception class, exported in low-cardinality metrics, and leaves the row observable for cleanup. The R14-style bounded cleanup job removes old `INIT`, `FAILED` and `DELETE_PENDING` objects; it never removes `READY` objects.

This is deliberately not an exactly-once distributed transaction between PostgreSQL and object storage. A deployment must monitor cleanup failures and retain the migration/verification procedure before deleting old local data.

## Migration from local uploads

R16 does not delete `uploads/` or run a mass migration at application startup. For an existing deployment:

1. stop or quiesce new uploads;
2. enumerate DB rows whose storage key is a local path;
3. upload each file to a server-generated object key;
4. verify size and SHA-256, then update the row to the object key/type;
5. verify representative downloads through every backend replica;
6. only then archive/remove the local volume.

If the local volume is empty, the cutover can start directly with MinIO. The migration is an explicit one-time operational task, not part of normal startup.

## Health and observability

MinIO contributes `objectStorage` to production readiness. It does not affect JVM liveness. Metrics use only fixed labels such as `operation`, `result` and `mediaType`; object keys, attachment IDs, filenames, checksums, URLs and credentials are never labels or log fields. No binary bytes are written to Redis.

No antivirus scanner or distributed MinIO cluster is included in R16. TLS termination, backup/replication and malware scanning remain deployment/security responsibilities.
