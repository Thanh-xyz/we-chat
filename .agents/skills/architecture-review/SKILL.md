---
name: architecture-review
description: Audit and improve the repository structure and software architecture of the WeChat Clone. Use when reviewing module boundaries, directory organization, dependency direction, duplicated responsibilities, frontend/backend separation, Docker infrastructure layout, or planning safe repository refactoring. Produce evidence-based findings and a target architecture before making structural changes.
---

# Architecture Review — WeChat Clone

## 1. Mission

Evaluate and improve the WeChat Clone repository structure while preserving runtime behavior, build compatibility, security boundaries, and deployment requirements.

Priorities:
1. Understand the actual repository before proposing changes.
2. Separate repository organization problems from application architecture problems.
3. Identify unnecessary coupling, unclear ownership, duplicated responsibilities, and misplaced files.
4. Design a maintainable target structure based on the project's actual technology stack.
5. Produce a verifiable, incremental refactoring plan.

Do not refactor files merely to make the directory tree look cleaner.

## 2. Repository discovery

Before making architectural recommendations:

1. Confirm the repository root and read applicable `AGENTS.md` instructions.
2. Inspect the current directory tree, Git status, existing project documentation, and build configuration.
3. Inspect the actual frontend, backend, Docker, gateway, infrastructure, scripts, and test directories.
4. Identify each module's responsibilities and its dependencies.
5. Inspect import paths, package declarations, Docker build contexts, Compose references, CI workflows, scripts, and deployment documentation before proposing file moves.
6. Distinguish current implementation from historical documentation and proposed future architecture.
7. Do not assume that every directory mentioned in documentation exists or that every configured service is actually used.

Prefer targeted inspection over dumping the entire repository into context.

Never expose secret values while examining configuration.

## 3. Architecture boundaries

Evaluate the following areas where present.

### Repository and infrastructure

Determine whether application source code, deployment configuration, development tooling, documentation, and generated artifacts have clear ownership.

Evaluate:
- Repository root clutter and duplicate configuration.
- Dockerfile locations and build contexts.
- Docker Compose organization and environment-specific overrides.
- Gateway/Nginx configuration and routing.
- CI workflows, scripts, migrations, and operational documentation.
- Separation of local development, testing, staging, and production configuration.

Do not require a monorepo or multiple repositories without evaluating the current development workflow and deployment needs.

### Frontend

Inspect:
- Feature and component boundaries.
- Separation of UI, state management, API clients, domain logic, and shared utilities.
- Routing, authentication, error handling, and environment configuration.
- Repeated components and duplicated business logic.
- Import direction, circular dependencies, and unclear ownership.
- Test organization and build configuration.

Prefer structure that supports the existing framework and application size. Avoid unnecessary abstraction and premature conversion to a different architectural pattern.

### Backend

Inspect:
- Package and domain boundaries.
- Controller, service, repository, and infrastructure responsibilities.
- Dependency direction and circular dependencies.
- Authentication, authorization, validation, and exception handling.
- Transaction boundaries and asynchronous processing.
- Database migrations, repository queries, and integration tests.
- Separation of domain logic from framework-specific and infrastructure code.

For the Spring Boot backend, assess whether existing domain packages have coherent responsibilities before proposing changes. Do not impose a new architecture solely because it is fashionable.

### Cross-service and runtime architecture

Trace actual communication paths between:
- Frontend and backend APIs.
- Gateway/Nginx and backend instances.
- WebSocket/STOMP clients and backend instances.
- Redis Pub/Sub and realtime message propagation.
- Backend and PostgreSQL.
- Backend and object storage.

Distinguish services that run inside Docker Compose from externally managed services.

Check service discovery, internal/public port exposure, healthchecks, persistent data, secrets, and environment-variable ownership where relevant.

Do not assume that a directory named `gateway`, `redis`, or `nginx` represents an independently deployed application.

## 4. Findings and evidence

For every finding, report:

- ID and severity: Critical, High, Medium, or Low.
- Category: repository structure, module boundary, dependency, configuration, deployment, security, or testing.
- Evidence: specific file paths, symbols, configuration references, or verified command output.
- Current behavior and why it is problematic.
- Impact and likelihood.
- Recommended change.
- Dependencies and potential regressions.
- Verification method.
- Whether the finding is confirmed or still requires investigation.

Do not label stylistic preferences as architectural defects.

Do not claim a dependency cycle, unused module, duplicate configuration, or broken deployment path without supporting evidence.

## 5. Target architecture design

After discovery, propose a target repository tree that fits the existing project.

For each proposed move or restructuring:
1. Explain why the current location is problematic.
2. Identify every known reference that could be affected.
3. Explain the intended ownership and dependency boundary.
4. List build, test, CI, Docker, Compose, script, and documentation updates required.
5. Identify compatibility risks and how to verify them.

Preserve existing technologies and working behavior unless a migration is explicitly approved.

Do not combine unrelated refactoring with feature changes.

Prefer the smallest structural change that meaningfully improves maintainability.

If two layouts are reasonable, compare them and recommend one based on repository evidence.

## 6. Safe refactoring workflow

Use these stages:

### Stage A — Baseline

Record the current Git state, repository layout, build commands, relevant test results, and known issues.

Do not discard or overwrite existing user changes.

### Stage B — Audit

Map module ownership, dependency direction, configuration references, and integration boundaries.

Separate confirmed defects from risks and optional improvements.

### Stage C — Target design

Present the proposed directory tree, rationale, affected paths, migration order, acceptance criteria, and rollback approach.

For significant architectural decisions, document alternatives and trade-offs.

### Stage D — Approval checkpoint

When the user requests an audit or plan, remain read-only.

Before a broad structural refactor, present the proposed changes and wait for approval unless the user has already explicitly authorized that exact scope.

Approval for one phase does not authorize unrelated changes or production deployment.

### Stage E — Incremental implementation

After authorization:
1. Make one coherent, reviewable group of changes at a time.
2. Preserve behavior and public interfaces.
3. Update imports, build configuration, scripts, Docker contexts, CI references, and documentation as required.
4. Avoid unrelated formatting and feature changes.
5. Inspect the complete diff after each group.

### Stage F — Verification

Run the applicable checks that are available:
- Frontend lint, tests, and production build.
- Backend tests and Maven build.
- Integration tests when the required services are available.
- Docker Compose configuration validation.
- Relevant path/reference checks.
- Service startup and integration checks when explicitly within scope.

Report commands and actual results. Distinguish passed, failed, skipped, and blocked checks.

A successful build alone does not prove the runtime architecture works.

## 7. WeChat Clone-specific invariants

Pay special attention to regressions involving:

- Frontend API URLs and environment-variable conventions.
- Spring Boot package declarations, resource loading, and Maven configuration.
- Flyway migration ordering and database compatibility.
- JWT authentication and authorization.
- WebSocket/STOMP authentication and topic authorization.
- Redis Pub/Sub between backend replicas.
- Gateway routing and WebSocket proxy headers.
- Object-storage access across backend replicas.
- Healthchecks, readiness, liveness, and service dependencies.
- Docker networking, persistent volumes, and secrets.
- CI scripts, build contexts, and deployment instructions.

Do not introduce new infrastructure or replace existing services simply to achieve a cleaner directory layout.

## 8. Explicit boundaries

Unless specifically authorized, do not:
- Deploy to a server or modify production resources.
- Change sandbox or approval policies.
- Change Git remotes, history, or repository permissions.
- Delete files or directories whose usage has not been established.
- Rewrite database migrations or public API contracts as part of a directory-only refactor.
- Introduce new frameworks, dependencies, repositories, or services without justification and approval.
- Print credentials or secret values.

If repository evidence is incomplete, document the uncertainty and investigate before making a recommendation.

## 9. Final report

For an audit, provide:

1. Executive summary.
2. Current repository and runtime architecture.
3. Confirmed findings ranked by severity.
4. Proposed target directory tree.
5. Current-to-target mapping for files and directories.
6. Dependency and compatibility risks.
7. Incremental refactoring plan.
8. Verification commands and acceptance criteria.
9. Open questions and approval requirements.

For implementation, additionally report files changed, checks run, remaining risks, and any incomplete work.

Never claim that the target architecture or deployment is verified unless the corresponding checks were actually performed.
