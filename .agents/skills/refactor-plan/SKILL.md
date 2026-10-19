---
name: refactor-plan
description: Create evidence-based, incremental refactoring plans for the WeChat Clone repository. Use after architecture review or when restructuring directories, modules, dependencies, configuration, or infrastructure layout. Define scope, file mappings, dependency order, risks, acceptance criteria, verification commands, and rollback steps before implementation. Planning only; do not modify project files.
---

# Refactor Plan — WeChat Clone

## 1. Mission

Translate verified architecture findings into an actionable, low-risk refactoring plan.

The goal is to improve repository structure, module boundaries, and maintainability without accidentally changing application behavior or breaking builds and deployments.

This skill plans changes. It does not execute the refactor.

Priorities:
1. Use evidence from the actual repository.
2. Separate required structural changes from optional cleanup.
3. Minimize the number and scope of affected files.
4. Identify dependencies before deciding the execution order.
5. Define measurable acceptance criteria for every phase.
6. Make risks, assumptions, and approval requirements explicit.

## 2. Inputs and discovery

Before planning:

1. Read the repository's `AGENTS.md` and applicable workflow instructions.
2. Read available architecture-review reports and relevant project documentation.
3. Inspect Git status and identify existing user changes.
4. Inspect the actual paths, imports, package declarations, build configuration, tests, Docker contexts, Compose references, CI workflows, and scripts affected by the proposed refactor.
5. Verify whether reported problems still exist.
6. Record unknowns that could materially change the plan.

Do not rely solely on an old audit report if the repository may have changed.

If no architecture audit exists, perform only the discovery necessary to produce a preliminary plan and clearly identify its limitations.

Never print secret values.

## 3. Define the refactoring scope

Classify proposed work into:

- Required: necessary to resolve a verified problem.
- Recommended: a meaningful improvement with a clear benefit.
- Optional: cosmetic or speculative cleanup that can be postponed.
- Out of scope: feature changes, unrelated bug fixes, technology migrations, or deployment changes not needed for the stated goal.

For the initial WeChat Clone restructuring, prioritize repository organization and module boundaries.

Do not automatically redesign the database, public API, authentication model, realtime protocol, or storage architecture as part of a directory refactor.

If a structural change necessarily affects runtime behavior, explain why and create a separate, explicitly approved workstream where appropriate.

## 4. Build a change inventory

For every planned change, identify:

- Change ID.
- Problem being addressed.
- Current path or responsibility.
- Proposed target path or responsibility.
- Exact known references that may need updating.
- Dependency on other changes.
- Expected benefit.
- Risk and complexity.
- Verification method.
- Rollback approach.

Do not invent file paths. Mark unresolved mappings as requiring investigation.

A proposed directory tree must distinguish existing paths from new proposed paths.

## 5. Dependency and execution order

Construct a dependency graph for the planned changes.

Consider:
- Shared utilities and common modules.
- Frontend import paths and API configuration.
- Backend Java package declarations and resource paths.
- Maven and frontend build configuration.
- Database migration discovery and ordering.
- Docker build contexts and COPY instructions.
- Compose file paths, mounts, networks, and environment files.
- Gateway/Nginx configuration.
- CI workflows, scripts, and documentation.
- Test discovery and fixture paths.

Order changes so that prerequisite changes occur first.

Group tightly coupled file moves and reference updates into one reviewable phase. Avoid combining unrelated frontend, backend, and infrastructure changes merely to reduce the number of commits.

## 6. Risk assessment

For each phase, evaluate:

- Build and test regressions.
- Runtime behavior changes.
- Import and package resolution failures.
- Docker image build failures.
- Configuration and environment-variable mismatches.
- Migration or resource-discovery regressions.
- Authentication, authorization, and security regressions.
- WebSocket routing and cross-instance realtime failures.
- Object-storage compatibility.
- CI and deployment workflow failures.
- Merge conflicts and interaction with uncommitted user changes.

Classify risks as Critical, High, Medium, or Low, with a short evidence-based explanation.

Distinguish verified risks from hypothetical risks.

Do not declare a change safe solely because it is a file move.

## 7. Phase design

Break the plan into small, independently reviewable phases.

For each phase provide:

1. Objective and rationale.
2. Exact scope and expected paths.
3. Prerequisites.
4. Dependency changes required.
5. Implementation steps.
6. Acceptance criteria.
7. Tests and verification commands.
8. Expected impact on other modules.
9. Rollback strategy.
10. Conditions requiring the work to stop.

Prefer phases that preserve a buildable and testable repository at each meaningful checkpoint.

Where possible, keep each phase focused enough to review in a single pull request.

Do not promise that every phase can be isolated into one commit if the dependency graph does not support it.

## 8. Verification strategy

Define verification before implementation.

Use the repository's actual scripts and build tools.

Depending on the affected paths, consider:
- Frontend lint, unit tests, and production build.
- Backend tests and Maven build.
- Integration tests and database migration checks.
- Docker Compose configuration validation.
- Docker image builds.
- Static checks for stale imports, references, and paths.
- Gateway configuration validation.
- Service startup and integration smoke tests.

Do not claim commands passed unless they were executed and their results observed.

If a command requires unavailable services, credentials, network access, or infrastructure, mark it as blocked and explain the limitation.

A successful compilation does not prove production readiness.

## 9. Git and checkpoints

Coordinate with the `git-workflow` skill when branch or history decisions are relevant.

Recommend a safe branch and checkpoint strategy based on the repository's existing conventions.

Do not create branches, stage files, commit, push, merge, rebase, or modify Git configuration as part of planning.

Do not discard user changes or assume the working tree is clean.

Identify natural review and recovery checkpoints for each phase.

## 10. Approval and execution boundaries

This skill is planning-only.

- Do not edit, create, delete, move, or rename project files.
- Do not install dependencies or skills.
- Do not run deployment or change external infrastructure.
- Do not modify environment variables, secrets, sandbox policy, or approval policy.
- Do not execute destructive Git operations.
- Do not silently expand the agreed scope.

Present the plan and wait for approval before implementation.

Approval of the overall direction does not automatically authorize every optional change or unrelated migration.

If the user explicitly asks to implement a particular phase, hand off to an appropriate implementation workflow and remain within the approved scope.

## 11. Required deliverable

Produce a refactoring plan containing:

1. Executive summary and intended outcome.
2. Current-state evidence and relevant audit findings.
3. Proposed target directory tree.
4. Current-to-target path mapping.
5. Scope: required, recommended, optional, and excluded work.
6. Dependency graph and phase order.
7. Phase-by-phase implementation checklist.
8. Risk register with severity and mitigation.
9. Acceptance criteria and verification commands.
10. Rollback strategy and checkpoints.
11. Unresolved questions and decisions requiring approval.

Finish by recommending the smallest safe first phase.

Do not start implementing the plan.
