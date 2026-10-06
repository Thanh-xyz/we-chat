# Notification fanout architecture

Message and conversation writes publish `NotificationEvent` inside their existing transaction. `NotificationEventPublisher` registers an `afterCommit` callback, so a rollback never schedules notification work. After commit, `NotificationEventListener` hands the event to `InProcessNotificationDispatcher`.

```text
transactional message/conversation write
        |
        +-- rollback -> no notification event
        |
        +-- commit -> bounded in-process executor
                         |
                         +-- REQUIRES_NEW notification transaction
                              recipient/preference batch reads
                              notification + delivery batch writes
                              realtime publish after notification commit
```

The executor defaults are core `2`, max `8`, queue `1000`, notification batch size `100`, and shutdown wait `PT10S`. They are configuration-driven under `app.notification.executor.*`. `AbortPolicy` supplies backpressure: a full queue increments `notification_dispatch_rejections_total` and drops only the process-local dispatch task; it never runs the whole fanout on the message request thread or grows an unbounded queue. Executor active tasks, queue size, and completed tasks are exposed as low-cardinality gauges.

`NotificationService` preserves current semantics: direct and group message events go to active conversation members except the actor; mentions take precedence over a regular message notification when enabled; disabled preferences suppress the corresponding notification; reactions target the message sender; explicit group/system recipient lists are honored (including a removed member receiving the explicit removal event); muted state is not consulted by the existing notification policy; and recipients are deduplicated. Recipient discovery, preferences, unread counts, notification rows, and delivery rows use batch operations. Notification ordering is not a strict cross-task guarantee. Direct friend-request notifications continue to use the existing synchronous `createNotification` path because they are not recipient fanout events.

Task failures are isolated from the committed message and recorded in `notification_dispatch_total{type,result}` plus `notification_dispatch_duration_seconds{type,result}`. No automatic retry is used because the current schema has no durable event/idempotency key for replay-safe delivery.

The notification fanout dispatcher is still process-local and is not a durable queue. After its notification transaction commits, `RealtimeBroker` uses the R5 Redis Pub/Sub bridge in production, so another JVM can deliver the live event to its local WebSocket session. Redis Pub/Sub remains best-effort: a Redis outage or process crash can lose the live realtime event even though the notification row is durable. A future outbox plus replay-capable worker can replace `NotificationDispatcher`/`RealtimeBroker` without changing message persistence or WebSocket authorization.
