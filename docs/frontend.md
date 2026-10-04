# Frontend production architecture and features

The frontend is a React 19 and Vite 8 single-page application for the existing Spring Boot chat API. It uses only real backend data and does not manufacture presence, unread counts, previews, or typing activity.

## Architecture

```text
React Router
  -> auth / protected route guards
  -> feature stores (auth, chat, realtime)
  -> centralized Axios API services
  -> Spring REST API

Authenticated app session
  -> one STOMP client
  -> /user/queue/conversation-events
  -> /topic/users/{currentUserId}/notifications
```

Important source areas:

- `src/features/auth`: session lifecycle and authentication state.
- `src/features/conversations`: conversation, cursor page, message, unread, and typing state.
- `src/features/realtime`: connection state and the bridge from STOMP events into chat state.
- `src/services/api`: centralized REST client and endpoint-specific services.
- `src/services/websocket`: the single STOMP client and bounded reconnect policy.
- `src/components`: accessible auth, conversation, and message UI.

The project remains JavaScript because the existing scaffold was JavaScript. API boundaries are isolated in services and state stores so a later TypeScript migration does not require changing component behavior.

Production uses an independent `frontend` container for the React/Vite build and static Nginx server. `gateway` does not build or copy frontend assets; it only routes `/` to `frontend:8080`, `/api/*` and `/ws` to `backend:8080`.

## Environment

Copy `wechatfrontend/.env.example` to `wechatfrontend/.env.local` for local development:

```dotenv
VITE_API_BASE_URL=/api
VITE_WS_URL=
VITE_DEV_PROXY_TARGET=http://localhost:8080
```

- `VITE_API_BASE_URL` defaults to the same-origin `/api` gateway path.
- When `VITE_WS_URL` is empty, the client derives same-origin `ws(s)://<host>/ws`.
- `VITE_DEV_PROXY_TARGET` makes Vite proxy `/api` and `/ws` to the local backend.

All `VITE_*` values are public browser configuration. Never put JWT secrets, database credentials, SMTP credentials, or any other secret in these values.

## Development and verification

```bash
cd wechatfrontend
cp .env.example .env.local
npm install
npm run dev
```

Run the quality checks:

```bash
npm test
npm run lint
npm run build
npm run test:e2e -- --list
```

The Docker workflow is run from the repository root:

```bash
docker compose --env-file .env -f docker/docker-compose.yml config
docker compose --env-file .env -f docker/docker-compose.yml build frontend gateway backend
docker compose --env-file .env -f docker/docker-compose.yml up -d
```

The Playwright suite contains a gateway SPA smoke test, an optional authenticated single-user flow, and an optional real two-browser realtime flow. Set `E2E_BASE_URL`, `E2E_EMAIL`/`E2E_PASSWORD`, or both user pairs plus `E2E_CONVERSATION_ID` to enable the authenticated cases. The two-browser case must run against the actual Compose gateway, backend, WebSocket broker, and database.

## Authentication and token storage

Login sends the backend's actual `{ identifier, password }` contract. The response contains an access token, rotating refresh token, and expiry.

- The access token is held in memory only.
- The refresh token is held in `sessionStorage`, not persistent `localStorage`.
- A page reload exchanges the refresh token before loading `/api/users/me`.
- A `401` triggers one shared refresh operation and retries the original request once.
- Refresh failure clears the session and returns the user to `/login` without a redirect loop.
- Logout attempts server revocation and always clears local credentials.

The backend currently returns refresh tokens in JSON and does not provide an HttpOnly cookie contract. The frontend follows that contract without changing backend authentication architecture.

Register, email verification, forgot-password, and reset-password pages use the backend DTOs and endpoints directly.

## Message history and reverse scrolling

The first request is:

```text
GET /api/conversations/{id}/messages?limit=50
```

Older pages replay the opaque `nextCursor` exactly:

```text
GET /api/conversations/{id}/messages?limit=50&cursor={nextCursor}
```

The UI never parses the cursor and never uses offset/page pagination for message history. Before prepending an older page, the message list captures `scrollHeight` and `scrollTop`; after render it applies the height difference so the visible messages stay anchored.

Requests are canceled with `AbortController` when the active conversation changes. Messages are normalized into chronological UI order and deduplicated by server message ID.

## Realtime flow

STOMP connects to `/ws` with the native header:

```text
Authorization: Bearer <access-token>
```

Only one client exists for the authenticated app session. It subscribes to the current user's permitted queue and notification topic. Typing events are sent to `/app/conversations/{id}/typing`, throttled, and stopped after idle time.

The backend `message.created` event contains a message ID and attachment metadata, but not the complete message DTO. On that event the client resynchronizes the newest cursor page and merges by server ID. This also prevents the HTTP response and WebSocket event from displaying the same message twice.

Reconnect delay grows from 1 to 30 seconds and stops automatically after six retry windows. The small connection badge exposes a manual retry. Successful reconnect restores the two session subscriptions and uses the current access token.

## Responsive and accessible UI

- Desktop and tablet use a conversation sidebar plus message panel.
- Mobile shows either the list or detail route, with a semantic back button.
- Login, list, history, and composer have loading, empty, error, and disabled states where applicable.
- Forms have labels; icon-only controls have accessible names; keyboard focus is visible.
- Enter sends a message and Shift+Enter preserves a newline.
- Reduced-motion preferences are respected.

## Phase 2 feature coverage and known backend gaps

- `ConversationResponse` does not include the latest message text. The list shows a generic server-backed state until that conversation's messages have been loaded; it does not issue an N+1 request per conversation.
- Direct conversations expose member IDs but no member profile summary, so unnamed direct chats use the neutral label “Trò chuyện trực tiếp”.
- `message.created` does not contain a full message DTO and there is no single-message GET endpoint, requiring newest-page resynchronization.
- Attachments support client-side MIME/size validation, queued/uploading/success/failed/cancelled states, upload-before-send, image preview, audio playback, and authenticated file downloads using the backend attachment contract.
- Contacts use the backend friend request, friend list, block/unblock, and user search endpoints with debounced search.
- The notification center loads real notifications, unread counts, mark-read/read-all, deletion, and realtime user notification events.
- Drafts are retained in memory per conversation; no credentials are persisted with drafts.
- Presence/online status is not displayed because no reliable realtime presence contract exists.
- Browser push, calls, reactions, edit/delete UI, advanced friend management, and advanced search are outside this phase.
- Playwright is included as an opt-in runtime suite. It is intentionally not reported as passed unless a running gateway/backend/database stack and real test credentials are supplied.
