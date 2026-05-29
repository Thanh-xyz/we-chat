# Auth API

Base path: `/api/auth`

## Register

`POST /api/auth/register`

```json
{
  "username": "thanh",
  "email": "thanh@example.com",
  "password": "Password123",
  "displayName": "Thanh"
}
```

Returns `201 Created` with an access token, refresh token, token expiry, and user profile.

## Login

`POST /api/auth/login`

`identifier` accepts either username or email.

```json
{
  "identifier": "thanh@example.com",
  "password": "Password123"
}
```

Returns `200 OK` with a new access token and refresh token.

## Refresh

`POST /api/auth/refresh`

```json
{
  "refreshToken": "refresh-token-value"
}
```

Refresh tokens are stored as SHA-256 hashes and rotated on every successful refresh. Reuse of a revoked refresh token revokes all active refresh tokens for that user.

## Logout

`POST /api/auth/logout`

```json
{
  "refreshToken": "refresh-token-value"
}
```

Returns `204 No Content` and revokes the provided refresh token.

## Authenticated Requests

Send the access token as a bearer token:

```http
Authorization: Bearer <access-token>
```

## Required Production Config

- `JWT_SECRET`: at least 32 characters.
- `JWT_ISSUER`: issuer name, defaults to `wechat`.
- `JWT_ACCESS_TOKEN_TTL`: ISO-8601 duration, defaults to `PT15M`.
- `JWT_REFRESH_TOKEN_TTL`: ISO-8601 duration, defaults to `P30D`.
