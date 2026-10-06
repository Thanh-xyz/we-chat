# Distributed realtime bridge (R5)

The browser protocol is unchanged. Browsers still connect to `/ws`, authenticate with STOMP `CONNECT`, and subscribe to the existing destinations:

- `/user/queue/conversation-events`
- `/topic/users/{userId}/notifications`
- `/topic/users/{userId}`

Only the backend-to-backend transport changes. `RealtimeEventPublisher` remains the single outbound abstraction and registers its work with the existing `afterCommit` hook. The local implementation sends directly to the Spring Simple Broker. The production implementation serializes an allowlisted envelope and publishes it to one configured Redis Pub/Sub channel.

```text
DB transaction
     |
     +-- rollback ----------------------> no realtime publish
     |
     +-- commit -> bounded publisher executor -> Redis Pub/Sub channel
                                                        |
                         +------------------------------+------------------+
                         |                                                 |
                    backend-1 subscriber                              backend-2 subscriber
                         |                                                 |
                  local Simple Broker                              local Simple Broker
                         |                                                 |
                    socket sessions                                  socket sessions
```

## Envelope and validation

Each message contains `version`, `eventId`, `originInstanceId`, `destination`, `eventType`, JSON `payload`, and `createdAt`. The destination policy accepts only the three existing browser destination families, and the event type policy accepts the current outbound event types found in the application. Unknown destinations, event types, malformed JSON, unsupported versions, null payloads, and messages above `REALTIME_MAX_EVENT_BYTES` are dropped before local delivery.

The listener maintains a bounded, ten-minute in-memory event-id cache per JVM. This suppresses duplicate delivery of the same envelope to one process, but it is not a durable idempotency store.

## Failure and backpressure behavior

- Redis commands use finite connect/command timeouts.
- Publisher and listener executors have bounded queues and explicit rejection metrics.
- A Redis publish failure is logged with event type, instance, and exception class only; payloads and tokens are never logged.
- A malformed or oversized inbound event is dropped and counted.
- Redis health is exposed as `realtimeRedis` and included in the production readiness group.
- Local/test mode remains available with `REALTIME_DISTRIBUTED_ENABLED=false`; the prod profile rejects that mode at startup.

## Honest limitations

Redis Pub/Sub is transient. It does not replay events after a subscriber reconnects, does not provide durable ordering, and does not make notification persistence durable. A committed database write can therefore outlive a lost realtime notification. A future transactional outbox and replay-capable worker can be introduced behind `RealtimeBroker` without changing the browser protocol.
