# Audit Log API

Base path: `/api/admin/audit-logs`

Audit endpoints are admin-only:

- `AUDIT_READ`: list and view detail.
- `AUDIT_EXPORT`: export CSV.

All audit JSON values are sanitized before persistence. Do not send or store raw passwords, access tokens, refresh tokens, verification tokens, `Authorization` headers, storage secrets, private URLs, or private file paths.

## List Audit Logs

`GET /api/admin/audit-logs`

Supported query params:

- `action`
- `actorUserId`
- `targetUserId`
- `conversationId`
- `messageId`
- `resourceType`
- `resourceId`
- `result`: `SUCCESS` or `FAILED`
- `from`, `to`: ISO-8601 timestamps
- `limit`: max 200
- `offset`

Example:

```http
GET /api/admin/audit-logs?action=MESSAGE_EDIT&result=SUCCESS&limit=50&offset=0
Authorization: Bearer <admin-access-token>
X-Request-Id: postman-audit-list-001
```

Response:

```json
{
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "actorUserId": "22222222-2222-2222-2222-222222222222",
      "actorUsername": "admin",
      "actorEmail": "admin@example.com",
      "action": "MESSAGE_EDIT",
      "resourceType": "MESSAGE",
      "resourceId": "33333333-3333-3333-3333-333333333333",
      "targetUserId": null,
      "conversationId": null,
      "messageId": "33333333-3333-3333-3333-333333333333",
      "requestId": "postman-audit-list-001",
      "traceId": "postman-audit-list-001",
      "result": "SUCCESS",
      "failureReason": null,
      "createdAt": "2026-06-16T05:00:00Z"
    }
  ],
  "total": 1,
  "limit": 50,
  "offset": 0
}
```

## Detail

`GET /api/admin/audit-logs/{id}`

Returns the list fields plus `beforeValue`, `afterValue`, `metadata`, `ipAddress`, and `userAgent`.

## Export CSV

`GET /api/admin/audit-logs/export`

Export requires `from` and `to`. The maximum range is 31 days and the maximum row count is 10000.

```http
GET /api/admin/audit-logs/export?from=2026-06-01T00:00:00Z&to=2026-06-16T23:59:59Z&action=AUTH_LOGIN_FAILED
Authorization: Bearer <admin-access-token-with-AUDIT_EXPORT>
```

## Postman Test Checklist

- Admin with `AUDIT_READ` can list audit logs.
- Regular user gets `403` on `GET /api/admin/audit-logs`.
- Filter by `action=MESSAGE_EDIT`.
- Filter by `actorUserId`.
- Filter by `from` and `to`.
- View detail by id.
- Export CSV with `AUDIT_EXPORT`.
- Edit a message, then verify a `MESSAGE_EDIT` log.
- Update user roles, then verify a `USER_ROLE_CHANGE` log.
- Attempt failed login, then verify an `AUTH_LOGIN_FAILED` log with `result=FAILED`.
- Send metadata with `password`, `token`, `refreshToken`, or `authorization`, then verify values are stored as `***MASKED***`.

## Production Notes

- Keep hot audit logs for 90-180 days.
- Keep security-critical logs up to 1 year if compliance requires it.
- Do not hard-delete audit rows from operational tables without an approved retention/archive job.
- Archive old partitions to cold storage before deletion.
- For high volume, partition `audit_logs` monthly by `created_at`.
- Ship audit events to ELK/OpenSearch/Kafka later from the same service boundary or an outbox pipeline.
