---
name: frontend-ui-ux
description: Translate WeChat Clone user journeys and business requirements into accessible, responsive React interfaces and interaction specifications. Use for UX states, forms, feedback, responsive behavior, accessibility, and chat interaction design; do not change backend contracts or unrelated screens.
---

# Frontend UI/UX — WeChat Clone

## Purpose

Turn approved user requirements and flows into clear, consistent, accessible frontend behavior. Make state and interaction decisions explicit before code changes with material UX impact.

Use this Skill for login and recovery flows, conversation switching, message lists, unread indicators, attachments, notifications, reconnect feedback, forms, destructive actions, and responsive or accessibility work.

## Required discovery

1. Read `AGENTS.md` and `.agents/projects/wechat-clone/WORKFLOW.md`.
2. Inspect `git status --short` and preserve existing changes.
3. Read the supplied BA/Scout/plan output when available; do not invent personas, requirements, or metrics.
4. Inspect actual routes in `src/App.jsx`, guards, pages, components, feature stores/controllers, API services, and relevant tests.
5. Confirm the current styling and component conventions before proposing new UI patterns.
6. Identify whether an interaction depends on REST, authentication, attachment, or STOMP behavior and record that dependency.

## UX workflow

For each flow, define:

- actor, entry point, preconditions, and primary goal;
- normal, loading, empty, error, success, disabled, pending, and retry states as applicable;
- validation rules and user-visible feedback;
- keyboard order, focus movement, semantic HTML, labels, contrast, and non-pointer access;
- responsive behavior for supported viewport sizes;
- cancellation, stale requests, duplicate actions, reconnect, and partial-failure behavior;
- analytics or metrics only when explicitly required, never by assumption.

For chat-specific flows, account for message ordering, conversation selection, unread state, attachment progress, notification state, reconnect indication, and REST resynchronization without claiming realtime guarantees that were not verified.

## Decision rules

- Resolve material ambiguity before implementation; otherwise document a bounded assumption.
- Reuse the existing design system and primitives. Coordinate with `frontend-design-system` rather than creating one-off styles.
- Keep client validation helpful but never treat it as authorization.
- Do not silently change routes, API payloads, error contracts, or permissions.
- Treat destructive actions as requiring clear scope, confirmation or undo where appropriate, and a failure state.
- Prefer accessible native controls and visible focus over custom interaction that duplicates browser behavior.
- Do not redesign unrelated screens to improve consistency.

## Boundaries

This Skill does not change backend, database, gateway, API, authentication, or WebSocket contracts. It does not invent missing server behavior or implement unrelated application features. If the requested UX requires such a change, record it as a dependency and stop at the approved boundary.

Frontend edits are allowed only when the user has approved the UI implementation scope. Otherwise produce a UX decision guide for `frontend-engineering`.

Never print secrets, modify `.env`, deploy, change sandbox/approval policy, or automatically invoke another Skill.

## References

- Read [references/accessibility-checklist.md](references/accessibility-checklist.md) when designing or reviewing interactive UI states, keyboard behavior, or responsive layouts.

## Required report and handoff

Report:

1. User flow and objective.
2. Current UI evidence and affected routes/components.
3. State matrix and accessibility/responsive decisions.
4. Assumptions, open questions, dependencies, and out-of-scope behavior.
5. Files changed or proposed.
6. Actual checks and `PASS`, `FAIL`, `SKIPPED`, or `BLOCKED` results.
7. Risks and next-step recommendation with P0/P1/P2 priority, exact Skill, evidence, and required approval.
8. Workflow stage and handoff artifact.

Use `frontend-engineering` for approved UI implementation, `frontend-quality-gate` for verification, and `frontend-integration-review` when the flow crosses backend or STOMP contracts. The route is `SCOUT → BA → PLAN → COOK → TEST → REVIEW`; implementation runs through the `cook` Skill, which must not be invoked automatically.
