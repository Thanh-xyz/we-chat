---
name: refactoring
description: Safely implement approved refactoring plans for the WeChat Clone repository. Use when restructuring directories, moving modules, improving dependency boundaries, extracting responsibilities, or simplifying code while preserving behavior. Require a clear scope, protect existing user changes, update all affected references, and verify each change incrementally.
---

# Refactoring — WeChat Clone

## 1. Mission

Implement approved refactoring work in small, verifiable increments.

The objective is to improve maintainability, module boundaries, and repository organization while preserving existing application behavior.

This skill executes approved changes. It does not replace architecture review or refactor planning.

## 2. Preconditions

Before editing:

1. Read the applicable `AGENTS.md`, project workflow, and relevant skill instructions.
2. Identify the approved refactor plan and the exact phase to implement.
3. Inspect the current repository state and relevant files.
4. Run or record the available baseline checks when practical.
5. Identify existing user changes and preserve them.
6. Confirm prerequisites, dependencies, and acceptance criteria.
7. Identify any discrepancy between the approved plan and the current code.

If no approved plan exists, the scope is unclear, or the repository has materially changed, stop and request clarification or an updated plan.

Do not interpret approval for one phase as authorization for the entire refactor.

## 3. Preserve behavior

Before changing code, understand its existing responsibilities and callers.

Unless the approved plan explicitly requires behavioral changes:

- Preserve public API contracts.
- Preserve authentication and authorization behavior.
- Preserve database semantics and migration history.
- Preserve WebSocket/STOMP behavior and access controls.
- Preserve Redis Pub/Sub and cross-instance message propagation.
- Preserve object-storage behavior and access permissions.
- Preserve error handling, validation, and configuration semantics.
- Preserve frontend routes, API integration, and user-facing behavior.

Do not mix new features, unrelated bug fixes, and structural refactoring in one change set.

If a necessary behavior change is discovered, document it separately and seek approval.

## 4. Safe file and module changes

For every approved change:

1. Identify the files and dependencies in scope.
2. Inspect imports, package declarations, references, and configuration.
3. Determine whether files are tracked, untracked, generated, or user-owned.
4. Make the smallest coherent change.
5. Update every affected reference.
6. Inspect the diff immediately after the change.
7. Run the most relevant targeted verification.
8. Continue only when the acceptance criteria for the current step are satisfied.

Do not:
- Move files based only on their names.
- Delete code merely because it appears unused.
- introduce abstraction without a clear need.
- Perform broad formatting changes unrelated to the task.
- Replace established technologies or architectural patterns without approval.
- Overwrite user changes or regenerate files in ways that discard manual edits.

Before removing any code, classify it: SAFE (no references, including tests), CAUTION (referenced only dynamically or by tests), or DANGER (public API, Spring bean or configuration, Flyway, reflection, `@Scheduled`, STOMP destination, or frontend route). Remove only approved SAFE items, one at a time, with checks before and after; report CAUTION and DANGER items instead of deleting them. If a check fails after a removal, restore that item before investigating further.

## 5. WeChat Clone-specific implementation checks

### Frontend

When changing frontend structure, inspect:

- Import paths and aliases.
- Component ownership and feature boundaries.
- Router configuration and lazy-loaded modules.
- API clients and environment variables.
- Shared state and authentication flows.
- Tests, fixtures, assets, and build configuration.
- Package manifests and lockfiles.

Run the relevant lint, tests, and production build using commands defined by the repository.

### Backend

When changing Spring Boot structure, inspect:

- Java package declarations and imports.
- Controller, service, repository, and domain boundaries.
- Spring component scanning and configuration discovery.
- Dependency injection and transaction boundaries.
- Resources, profiles, serialization, and exception handling.
- Flyway migration discovery and ordering.
- Unit and integration test package paths.

Do not rewrite historical database migrations to accommodate a directory refactor without explicit approval.

### Docker and infrastructure

When changing infrastructure paths, inspect:

- Dockerfile locations and build contexts.
- COPY and ADD instructions.
- Docker Compose file references and relative paths.
- Bind mounts and named volumes.
- Nginx configuration and upstream references.
- Environment-variable files and interpolation.
- CI workflow paths and deployment scripts.

Validate Compose configuration and build affected images when permitted and feasible.

Do not deploy to a remote environment unless separately authorized.

## 6. Incremental verification

Use the following verification loop:

1. Make one coherent change.
2. Inspect `git diff` and `git status`.
3. Run targeted tests or static checks.
4. Fix regressions introduced by the change.
5. Run broader checks at phase boundaries.
6. Compare the result against the approved acceptance criteria.
7. Report any blocked or skipped checks.

Use the actual repository commands rather than assuming conventional command names.

Distinguish:
- Passed: the check ran and succeeded.
- Failed: the check ran and failed.
- Skipped: the check was intentionally not run.
- Blocked: prerequisites or permissions prevented execution.

Never claim that a build, test, or integration works without verifying it.

## 7. Git safety

Follow `git-workflow` when Git operations are relevant.

- Inspect the branch and working tree before changes.
- Keep modifications within the approved scope.
- Preserve unrelated staged, unstaged, and untracked files.
- Review staged and unstaged diffs separately.
- Do not blindly stage all files.
- Do not commit, push, merge, rebase, reset, or clean unless explicitly authorized.
- Do not amend or rewrite existing commits without authorization.
- Never use destructive commands to hide unexpected changes or failures.

If the worktree contains unexpected changes, determine their relevance before proceeding.

## 8. Failure handling and rollback

If a change breaks a build, test, import, or runtime assumption:

1. Stop expanding the refactor.
2. Identify the earliest failing change.
3. Capture the relevant error and affected paths.
4. Attempt a minimal, in-scope correction.
5. Re-run the failed verification.
6. If recovery would require discarding user work, broad reversals, or unapproved architectural decisions, stop and request guidance.

Do not automatically reset or restore the repository to a presumed clean state.

Prefer small changes that can be reviewed and reverted independently.

## 9. Scope and authorization

Only implement the approved phase.

Seek approval before:
- Expanding into another module or service.
- Changing API contracts or runtime behavior.
- Introducing dependencies or infrastructure.
- Altering database schemas or migration history.
- Deleting files whose purpose has not been established.
- Changing security-sensitive authentication or authorization behavior.
- Performing destructive Git operations.
- Deploying or changing remote infrastructure.

Do not modify sandbox settings, approval policies, secrets, or credentials to bypass restrictions.

## 10. Completion report

At the end of each phase, report:

1. Scope completed.
2. Files created, moved, modified, or deleted.
3. Important dependency or configuration changes.
4. Verification commands and actual results.
5. Acceptance criteria met or unmet.
6. Remaining risks, blockers, and follow-up work.
7. Any changes requiring further approval.

Do not claim the entire repository is production-ready merely because one refactoring phase passed its tests.

Stop at the end of the approved scope.
