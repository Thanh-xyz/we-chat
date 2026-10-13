---
name: wechat-flow-trace
description: Trace a WeChat repository behavior across API, service, persistence, realtime, and frontend boundaries for onboarding, impact analysis, or a focused code review.
---

# Trace a behavior

Use the user-visible operation or failing event as the starting point. Locate its route in `wechatbackend/src/main/java/main/com/chat/wechat` or its call in `wechatfrontend/src/services/api` and `wechatfrontend/src/services/websocket`.

Follow only the relevant path through controller, service, JDBC repository or `FileStorageService`, transaction or after-commit publisher, and frontend store/component. Check related DTOs, Flyway migrations, tests, and `docs/api` or `docs/architecture` when they define the contract. Inspect both sides when the request crosses the API boundary.

Output the entry point, key state transitions, ownership and authorization checks, persistence or external dependencies, and exact file references. Mark any runtime behavior that source inspection alone cannot establish. Do not turn historical claims in `.agents/projects/wechat-clone/PROJECT-CONTEXT.md` into test results.
