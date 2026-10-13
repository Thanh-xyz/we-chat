---
name: wechat-runtime-verification
description: Verify WeChat Compose deployment, health, cross-replica behavior, and production-readiness acceptance with an evidence-based report.
---

# Runtime verification

Read `docs/deployment.md`, `docs/testing.md`, `docker/docker-compose.yml`, and the task-specific architecture document. Identify the actual services, health checks, gateway route, external dependencies, and test prerequisites before running commands. Do not print resolved Compose config or environment values that can contain secrets.

Choose the smallest evidence set that can answer the task. Separate static checks, focused unit tests, Testcontainers integration tests, and live Compose/browser checks. For a multi-replica claim, identify replicas and show which one handled each request; gateway success alone does not establish replica independence. Distinguish liveness from readiness, and transient Redis delivery from durable database state.

Report each requested criterion as PASS, FAIL, or BLOCKED with the command or observed result. A skipped test, absent Docker daemon, missing credentials, or historical prompt claim is not a pass. Preserve volumes and existing runtime data; changes to live infrastructure or data require the current task's authority.
