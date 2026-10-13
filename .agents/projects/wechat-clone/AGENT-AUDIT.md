# WeChat agent and skill audit

Date: 2026-10-13. Scope: this repository's Codex setup and the existing Agency catalog.

## Inventory and decision

There is no `./agents/` directory. The untracked `.agents/` tree contains 282 generated Codex TOML role files in `integrations/codex/agents/`; all parsed and had nonempty `name`, `description`, and `developer_instructions`, with no duplicate names. The source catalog is generic and spans many unrelated domains. It has no project `SKILL.md` files. The WeChat-specific files were task prompts and context, not Codex-discoverable agents.

The generated catalog files remain intact as optional reference or manual installation material. Their tool access is inherited from the invoking Codex session when installed; the files inspected do not establish access to Docker, Supabase, credentials, or an application runtime. No bulk install or deletion is justified for this project.

| Existing role or group | Useful responsibility | Gap against current repository | Decision |
|---|---|---|---|
| Codebase Onboarding Engineer | Read-only path tracing | Generic entry points; no WeChat API/JDBC/STOMP map | Reuse approach in `wechat-flow-trace`; retain catalog role |
| Backend Architect | Server-side design and API work | Broad microservice/guaranteed-order examples do not match this modular monolith and best-effort Pub/Sub | Retain as optional design reference; use `wechat-spring-data-change` for implementation |
| Frontend Developer | React UI | Covers multiple frameworks and editor integration; no current API/token/cursor contract | Retain reference; use `wechat-frontend-integration` |
| Realtime Collaboration Engineer | Live transport reasoning | Assumes durable replay, operation logs, and CRDT workflows absent here | Retain only as future design reference; use `wechat-realtime-contract` for current work |
| Database Optimizer | PostgreSQL query/index analysis | Says every migration needs a DOWN script; this repo uses versioned Flyway migrations without that convention | Retain for query-plan consultation; use `wechat-spring-data-change` for migration workflow |
| Database Reliability Engineer | Backup, failover, safe schema changes | Operational remit exceeds this Compose foundation and unverified recovery targets | Retain for a separately scoped reliability task |
| Identity & Access Engineer | Auth and authorization review | OAuth/SSO/SCIM focus exceeds current JWT/refresh and membership model | Retain for focused design review; trace current server and STOMP checks first |
| Security Architect | Trust-boundary review | Generic; no project-specific JWT, S3, gateway, or membership contract | Retain as optional independent reviewer for material security changes |
| DevOps Automator | Deployment automation | Assumes IaC/CI workflows not present in this repository | Retain reference; use `wechat-runtime-verification` for Compose checks |
| SRE (Site Reliability Engineer) | Runtime evidence and failure analysis | Generic SLO/chaos workflow lacks current health and two-replica criteria | Retain reference; use `wechat-runtime-verification` |
| Code Reviewer | Independent correctness review | Needs repository-specific contract and evidence boundaries | Consolidate active review into `wechat_code_reviewer`; retain original identifier in catalog |
| Minimal Change Engineer | Scope discipline | Duplicates a project-wide rule rather than a separate specialist task | Keep as catalog reference; apply through root `AGENTS.md` |
| Git Workflow Master | Git hygiene | Generic branching guidance does not add a recurring specialist need | Retain reference; no active project role |
| Prompt Engineer | Task brief quality | Generic prompt process; existing template already handles task briefs | Retain reference; update project template paths and context |
| API Tester and Test Automation Engineer | API and Playwright test design | No source-specific fixtures, endpoint contracts, or skip policy | Retain references; use affected domain skill plus runtime verification |
| WeChat Mini Program Developer | WeChat platform apps | This repository is a web chat clone, not a WeChat Mini Program | Exclude from project routing; retain catalog file |
| Other generated catalog roles | Unrelated specialties | No recurring task in this repository establishes relevance | Retain unchanged as user-provided catalog; do not activate wholesale |

## Active architecture

- Root `AGENTS.md` supplies project-wide conventions and evidence boundaries.
- `wechat_project_lead` owns scoped changes; `wechat_code_reviewer` performs independent read-only review. Built-in `explorer` remains available for source mapping.
- Six repo skills hold the repeatable procedures: flow tracing, Spring/data changes, realtime contracts, attachment storage acceptance, frontend integration, and runtime verification.
- `.agents/projects/wechat-clone/WORKFLOW.md` routes tasks to roles and skills. Historical Prompt 22/23 material remains available, but its reported results and permissions do not carry into a new task.

No existing generic role was deleted or renamed. Active names are distinct from the catalog's generated identifiers, so installing a generic role later does not silently override these project roles.

## Use in Codex

From the repository root or a subdirectory, ask Codex to use `wechat_project_lead` for a scoped change or `wechat_code_reviewer` for an independent review. For example: “Have `wechat_code_reviewer` review this branch's attachment authorization changes.” Codex may select a matching skill automatically; invoke one explicitly with `$wechat-storage-acceptance` or another skill name when you want that procedure. `AGENTS.md` is loaded as project guidance. New agent or skill files may require a fresh session or restart if the current client has cached its discovery list.

The supported locations and required fields were checked against the official [Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents), [Build skills](https://learn.chatgpt.com/docs/build-skills), and [AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md) documentation. These project files do not install the unrelated Agency catalog into a user's personal Codex directory.

## Maintenance

Update a skill when the repeated workflow or code contract changes. Update `PROJECT-CONTEXT.md` only after inspecting source. Keep historical prompts as records; write a new task brief or current user instruction for new work. Validate TOML parsing, skill frontmatter, referenced paths, and any scripts before distributing changes.
