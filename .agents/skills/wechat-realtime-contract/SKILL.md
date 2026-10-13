---
name: wechat-realtime-contract
description: Change or review WeChat STOMP authorization, Redis Pub/Sub delivery, typing, notifications, or client reconnect and resynchronization behavior.
---

# Realtime contract

Trace the event from the transactional writer and `RealtimeEventPublisher` through `RealtimeBroker`, Redis listener, local simple broker, STOMP destination policy, and `WebSocketManager` or feature store. Read `docs/architecture/realtime-redis.md` and the affected tests.

Verify destination allowlists and conversation or user authorization on inbound STOMP frames. For outbound changes, preserve after-commit publication and consider duplicate delivery, bounded queues, reconnect, and client resynchronization. Redis Pub/Sub is transient; do not claim replay, durable order, or guaranteed delivery. Notification persistence and live delivery have different failure modes.

Use focused backend and frontend tests. For cross-replica behavior, require a running two-replica test that identifies each replica and observes the client result. Report which guarantee is established by each check.
