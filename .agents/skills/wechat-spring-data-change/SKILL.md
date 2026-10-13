---
name: wechat-spring-data-change
description: Implement or review WeChat Spring Boot REST, service, JDBC, PostgreSQL, or Flyway changes that affect application data or API contracts.
---

# Spring and data changes

Read the relevant controller, DTO, service, JDBC repository, migration, and frontend API caller. Preserve the existing endpoint and error contract unless the task requires a change. Treat authorization and conversation membership as server-side checks; review `SecurityConfig` and repository predicates when access changes.

For schema work, inspect `wechatbackend/src/main/resources/db/migration` and add a new Flyway migration rather than editing an applied version. Consider existing rows, constraints, indexes, concurrent replicas, and rollback/recovery before data changes. Use `docs/testing.md` to choose verification: focused unit/service tests first; `-Ppostgres-integration` when PostgreSQL behavior or migrations matter. H2 tests do not establish PostgreSQL compatibility.

Report the contract changed, migration impact if any, tests run, and any unverified database or deployment condition. Do not run a migration against a live database unless the current task authorizes it.
