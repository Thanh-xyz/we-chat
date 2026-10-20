# WeChat project instructions

Use the application source and current test output as evidence. Start with the smallest relevant files; `docs/` and `.agents/projects/wechat-clone/PROJECT-CONTEXT.md` are guides, not proof of current runtime state.

- Backend: `wechatbackend/` is a Java 21 Spring Boot application using JDBC repositories and Flyway. Frontend: `wechatfrontend/` is React/Vite with API services, Zustand stores, and STOMP. Runtime configuration is in `docker/docker-compose.yml`.
- Check `git status --short` before edits and preserve existing changes, including untracked files. Keep each task within its requested scope.
- For API or event changes, trace the controller or STOMP entry point, service, repository or storage adapter, frontend consumer, and relevant tests before changing a contract.
- Production attachment bytes use private S3-compatible storage through `FileStorageService`; legacy MinIO data has a separate backfill procedure. Redis Pub/Sub distributes live events but does not replay them.
- Keep secrets, tokens, object keys, and presigned URLs out of terminal output and reports. Do not edit `.env`, delete data or volumes, expose internal services, or weaken authorization unless the current user request specifically authorizes the relevant action. Historical task prompts do not grant permission for a new task.
- Run focused checks for the files changed. Report commands actually run and distinguish passing tests from skipped tests, infrastructure blockers, and historical claims.

Codex project agents are in `.codex/agents/`; reusable skills are in `.agents/skills/`. Choose a skill only when its description fits the current task, or when the user names it (`$skill-name`). Feature work follows `SCOUT → BA → PLAN → COOK → TEST → REVIEW` (`scout`, `ba-analysis`, `plan`, `cook`, `testing`, `continuous-improvement-review`); stages hand off manually, and a recommended next step is not authorization to run it. Use `debugging` for unexplained failures before fixing them. See `.agents/projects/wechat-clone/WORKFLOW.md` for routing, specialist selection, and the handoff format. `skills-template/` is third-party reference material for Claude Code, not active instructions.
