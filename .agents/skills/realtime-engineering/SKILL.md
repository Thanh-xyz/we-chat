---
name: realtime-engineering
description: Implement or review WeChat Clone backend STOMP, Redis Pub/Sub, event authorization, routing, delivery, and duplicate handling. Use for server realtime changes; inspect client reconnect and resync as a contract dependency.
---

# Realtime Engineering — WeChat Clone

## Activation and ownership

Use when a change affects server STOMP frames or destinations, Redis Pub/Sub transport, event publication, duplicate handling, or subscription security. This Skill owns backend transport and server-side authorization mechanics. `frontend-engineering` owns client reconnect/resync implementation; `frontend-integration-review` checks client compatibility. `wechat-realtime-contract` remains the concise cross-layer guide.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, request/approved plan, and Git status. Preserve existing changes.
2. Trace the transactional writer → `RealtimeEventPublisher` → `RealtimeBroker`/Redis → `LocalRealtimeDelivery`/simple broker → STOMP destination → frontend `WebSocketManager`/store.
3. Inspect `WebSocketConfig`, channel authorization, destination policy, `docs/architecture/realtime-redis.md`, affected tests, and any gateway `/ws` behavior in scope.
4. Identify persisted source of truth separately from ephemeral notification transport.

## Implementation protocol

1. Specify event source, recipient, payload, authorization, destination, commit boundary, and client reconciliation.
2. Preserve after-commit publication for transaction-backed events. Check bounded queues, timeout/failure handling, allowlisted destinations/payloads, and sensitive logging.
3. Account for duplicate, reordered, missed, and delayed events; document what persisted REST state the client needs for recovery and hand client behavior to its frontend owner.
4. Make only approved backend changes; update focused backend tests and inspect the diff. Include frontend code/tests only if the approved task explicitly covers that layer and its owner coordinates the change.
5. For a cross-replica claim, run an isolated two-replica test that identifies both replicas and observes delivery. Otherwise mark the claim unverified.

Redis Pub/Sub is transient: it supplies no replay or durable ordering. Persisting a message does not prove its live delivery. A local broker test does not prove the Redis bridge or reconnect behavior.

## Quality gate and stop conditions

Completion requires evidence for changed destinations, server authorization, persistence relationship, failure path, and the client-facing event contract. Inspect the frontend consumer; report client behavior as verified only if its checks actually ran.

Stop before changing public STOMP contracts, permissions, storage semantics, database schema, or runtime topology outside approval. Do not publish test messages to shared/production systems without authorization; do not log event payloads or credentials; do not deploy or alter sandbox policy.

## Report and handoff

Report scope/result; event flow and files changed; actual commands and PASS/FAIL/SKIPPED/BLOCKED outcomes; failed, blocked, and unexecuted checks; remaining loss/duplicate/order risks and out-of-scope findings. Recommend next steps with P0/P1/P2, exact Skill, evidence, scope, and prerequisites, or state none.

Record SCOUT → BA → PLAN → COOK stage. PLAN selects this Skill for realtime risks and `frontend-integration-review` for client compatibility; use `testing` for independent cross-replica verification when justified. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Do not invoke another Skill automatically.
