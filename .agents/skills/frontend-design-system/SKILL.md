---
name: frontend-design-system
description: Establish and maintain a reusable, accessible design system for the WeChat Clone React/Vite frontend. Use when defining tokens, primitives, component variants, interaction states, or styling conventions; do not use for unrelated feature work or whole-page redesigns.
---

# Frontend Design System — WeChat Clone

## Purpose

Create or improve a coherent visual foundation for the existing frontend without changing product behavior or introducing a new styling technology by default.

Use this Skill when a task concerns design tokens, typography, spacing, colors, radii, shadows, breakpoints, shared UI primitives, component variants, or visual consistency.

Do not use it to redesign unrelated pages, change backend contracts, introduce a styling library without approval, or implement a feature whose main risk is API, state, or realtime behavior.

## Repository grounding

Before making recommendations, inspect the current frontend. The repository is currently React/Vite with JavaScript/JSX, CSS files, and `lucide-react`; do not assume TypeScript, CSS Modules, Tailwind, or another component library exists.

Inspect the actual:

- `wechatfrontend/src/styles-tokens.css` and `src/index.css`;
- shared and common components under `src/components`;
- page and feature usage under `src/pages` and `src/features`;
- Vite configuration, package scripts, assets, and relevant tests.

Treat this as current source evidence only after verifying it for the task.

## Workflow

1. Read `AGENTS.md` and `.agents/projects/wechat-clone/WORKFLOW.md`.
2. Run `git status --short` and preserve all existing changes.
3. Record the visual problem, affected screens, acceptance criteria, and non-goals.
4. Inventory existing tokens and repeated values before proposing new ones.
5. Identify the smallest reusable primitive or variant that solves the problem.
6. Check all known consumers, states, responsive behavior, and tests.
7. Implement only the approved frontend scope, or produce a design decision guide when implementation was not authorized.
8. Run focused lint, unit tests, and build checks defined by `package.json`.

## Design rules

- Reuse an existing token or primitive before adding a new value.
- Keep semantic tokens distinct from component-specific implementation details.
- Define component states explicitly: default, hover, focus-visible, active, disabled, loading, error, and selected where applicable.
- Preserve readable contrast, keyboard focus, hit targets, and reduced-motion behavior where relevant.
- Prefer composition and variants over near-duplicate components.
- Keep responsive changes tied to supported product layouts, not arbitrary breakpoint proliferation.
- Preserve existing branding unless a redesign is explicitly approved.
- Do not encode business rules, API calls, authentication, or WebSocket behavior in presentational primitives.
- Do not claim a visual issue is fixed without a rendered or otherwise direct verification when visual verification is part of acceptance.

## Boundaries and stop conditions

Stop and request clarification or planning approval when the change requires:

- redesigning a whole page or navigation model;
- changing route, API, authentication, or realtime behavior;
- adding a dependency or styling framework;
- changing unrelated components for consistency only;
- deleting a token or shared component whose consumers are not established.

Never expose secrets, modify `.env`, change sandbox or approval policy, deploy, or modify backend/database/infrastructure files.

## Required report

Report:

1. Objective, affected surfaces, and scope.
2. Existing tokens/components inspected and evidence.
3. Decisions, new or changed primitives, and affected consumers.
4. Files changed or proposed, with reasons.
5. Commands actually run and `PASS`, `FAIL`, `SKIPPED`, or `BLOCKED` results.
6. Accessibility, responsive, regression, and visual-verification limitations.
7. Next-step recommendation with priority P0/P1/P2, exact Skill name if useful, evidence, and required approval.
8. Current workflow stage and required handoff information.

Do not automatically invoke another Skill. In the current repository, hand off design-system implementation to `frontend-engineering` after an approved plan and use `frontend-quality-gate` for verification. This fits the `SCOUT → BA → PLAN → COOK → TEST → REVIEW` workflow; implementation runs through the `cook` Skill.
