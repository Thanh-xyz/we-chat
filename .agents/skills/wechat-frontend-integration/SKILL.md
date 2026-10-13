---
name: wechat-frontend-integration
description: Implement or review WeChat React client flows that depend on backend REST, attachment, authentication, or STOMP contracts.
---

# Frontend integration

Trace the UI event through component or page, feature hook/store, `wechatfrontend/src/services/api` or `wechatfrontend/src/services/websocket`, and the backend DTO or destination. Keep API calls in services and shared session/reconnect behavior in their existing owners. Check the actual backend contract before changing request fields, cursor use, attachment lifecycle, or token handling.

For message history, preserve opaque cursor replay and deduplication of HTTP and realtime results. For attachments, distinguish client preflight from server validation and do not persist signed URLs. For auth, respect the current memory access token and session refresh flow described in `docs/frontend.md`.

Run focused Vitest tests plus lint/build when relevant. The Playwright suite is runtime evidence only when the gateway and required credentials are available; report skipped authenticated cases explicitly.
