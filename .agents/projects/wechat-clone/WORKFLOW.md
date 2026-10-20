# WeChat Clone — Agent Workflow

Codex discovers project agents from `.codex/agents/*.toml` and repo skills from `.agents/skills/*/SKILL.md`. Invoke a skill explicitly with `$skill-name` (or `/skills`); otherwise Codex may select one whose description matches the task. Skill bodies load only when used, so keep descriptions precise. The generic Agency catalog is no longer part of this checkout.

## Operating principle

Dùng `agency-agents` như một nhóm chuyên gia có phân vai, nhưng giữ một luồng thực thi duy nhất và có bằng chứng. Mỗi task phải có một owner rõ ràng, một reviewer phù hợp và một Definition of Done đo được.

Không kích hoạt nhiều agent chỉ để tạo thêm ý kiến. Chọn agent theo loại rủi ro của task.

## Agent roster cho project này

| Need | Project agent | Skill |
|---|---|---|
| Hiểu repository và trace flow | built-in `explorer` or `wechat_project_lead` | `wechat-flow-trace` |
| Spring REST, JDBC, PostgreSQL, Flyway | `wechat_project_lead` | `wechat-spring-data-change` |
| WebSocket, Redis Pub/Sub, realtime | `wechat_project_lead` | `wechat-realtime-contract` |
| Storage upload/download, legacy migration boundary | `wechat_project_lead` | `wechat-storage-acceptance` |
| Frontend API and STOMP integration | `wechat_project_lead` | `wechat-frontend-integration` |
| Compose and runtime evidence | `wechat_project_lead` | `wechat-runtime-verification` |
| Independent scoped review | `wechat_code_reviewer` (read-only by instructions; inherits parent sandbox) | matching domain skill |
| Execute an approved plan (COOK) | `wechat_project_lead` | `cook` |
| Root-cause investigation | `wechat_project_lead` | `debugging` |
| Docker, Compose, gateway, runbooks | `wechat_project_lead` | `deployment-operations` |
| REVIEW stage and improvement checkpoints | `wechat_code_reviewer` (read-only by instructions; inherits parent sandbox) or `wechat_project_lead` (authorized remediation) | `continuous-improvement-review` |

The compact `wechat-*` skills remain the cross-layer guides. The specialist skills listed in the feature delivery workflow below add deeper procedures for a single concern.

## Sandbox and agent permissions

Codex subagents inherit the parent session's sandbox and approval settings. Agent TOML instructions do not create a separate read-only boundary, and `sandbox_mode` is not a per-agent override.

- Scout and reviewer instructions prohibit edits, but filesystem permissions still come from the parent session.
- For filesystem-enforced read-only work, start the parent Codex session in read-only mode before spawning the agent. In the CLI, a separate session can be started with `codex exec --sandbox read-only -C <repo> "<task>"`.
- When the main session is writable and an independent read-only review is required, use a separate Codex session with the read-only sandbox. If that is unavailable, describe the role as instruction-only read-only and do not claim sandbox enforcement.

## Feature delivery workflow

Canonical flow for a feature, bug fix, or change request:

```text
SCOUT → BA → PLAN → COOK → TEST → REVIEW
```

Stages are workflow steps. Skills are specialists that a stage selects. Specialist skills record their stage as `SCOUT → BA → PLAN → COOK`; read that as a position in this six-stage flow. TEST and REVIEW follow COOK. The `cook` Skill runs COOK: it executes the approved plan with the implementation skills selected in PLAN, owned by `wechat_project_lead`.

Every transition is a manual handoff. A recommendation does not authorize the next stage, and no Skill invokes another automatically. Skip or compress a stage only when its output already exists or the task is small enough. For example, a well-specified bug fix may skip BA, and a one-file fix may use a short plan. Record the reason in the handoff.

### Stages

| Stage | Owner skill(s) | Input | Output | Must not |
|---|---|---|---|---|
| SCOUT | `scout`; `wechat-flow-trace` for one behavior | request, repo state | impact map across layers, relevant files/tests/contracts, risks, likely specialists, blocking questions | edit files or runtime state |
| BA | `ba-analysis` | request, Scout report | requirements, business rules, verifiable acceptance criteria, edge/error cases, permissions, assumptions, open questions | design file-level changes; force clarification when requirements are already clear |
| PLAN | `plan`; `refactor-plan` for structural work | Scout + BA output | with either skill, complete Markdown plan saved under `docs/plans/YYYY/MM/DD/`; include the PLAN deliverables and the selected skill's required content | edit application files or existing docs; invoke another Skill; select every specialist |
| COOK | `cook` (by `wechat_project_lead`) + implementation skills selected in PLAN | approved plan | scoped changes, local checks, deviation and blocker notes, checkpoint findings | expand scope; change contracts, schema, or permissions outside the plan |
| TEST | `testing`; `frontend-quality-gate` for client changes; domain verification skills | changed files, acceptance criteria | PASS/FAIL/SKIPPED/BLOCKED/INCONCLUSIVE per criterion, coverage gaps, failure diagnosis | edit code to make a test pass; report planned checks as run |
| REVIEW | `continuous-improvement-review` (uses `code-review` procedure) | final diff, plan, TEST report | findings with severity/confidence/evidence, finding status, plan conformance, finish / return decision | rubber-stamp TEST results; review unrelated code as blocking |

### PLAN: specialist selection

PLAN decides which layers are affected from SCOUT evidence, not from the feature name. It selects the smallest sufficient set of specialists using the table below as a menu, not a checklist:

| Change touches | Design/decision skill | Implementation skill (COOK) | Verification skill (TEST/REVIEW) |
|---|---|---|---|
| Visual tokens, primitives, variants | `frontend-design-system` | `frontend-engineering` | `frontend-quality-gate` |
| User journey, forms, states, accessibility | `frontend-ui-ux` | `frontend-engineering` | `frontend-quality-gate` |
| Frontend module/store/routing structure | `frontend-architecture` | `frontend-engineering` or `refactoring` | `frontend-quality-gate` |
| REST request/response/error/pagination contract | `api-contract-design` | `spring-boot-engineering` + `frontend-engineering` | `frontend-integration-review`, `testing` |
| Client use of REST/auth/attachments/STOMP | — | `frontend-engineering` (`wechat-frontend-integration`) | `frontend-integration-review` |
| Backend package boundaries, transaction ownership | `backend-architecture` | `spring-boot-engineering` or `refactoring` | `testing` |
| Controller/service/JDBC behavior | — | `spring-boot-engineering` (`wechat-spring-data-change`) | `testing` |
| Identity, authorization, tokens, rate limits | `backend-security` | `spring-boot-engineering` | `testing` (negative paths), `backend-security` review |
| STOMP, Redis Pub/Sub, after-commit events, resync | `realtime-engineering` | `realtime-engineering` (`wechat-realtime-contract`) | `testing`; two-replica check only via `wechat-runtime-verification` |
| Measured latency/throughput/resources | `backend-performance` | `backend-performance` | re-measurement in `backend-performance` |
| Schema semantics, constraints | `data-modeling` | `database-migration` | `testing` (`-Ppostgres-integration`) |
| Flyway migration, schema-local backfill | `database-migration` | `database-migration` | `testing`, `data-quality-integrity` |
| Slow or incorrect SQL, indexes | `sql-query-optimization` | `sql-query-optimization` + `database-migration` for indexes | `testing` |
| Invariants, duplicates, orphans, idempotency | `data-quality-integrity` | `spring-boot-engineering` or `database-migration` | `data-quality-integrity` |
| Bulk/cross-system data movement | `data-pipeline-engineering` | `data-pipeline-engineering` | `data-quality-integrity` |
| Metrics, reporting datasets | `analytics-data-engineering` | `analytics-data-engineering` | `testing` |
| Attachment storage | — | `spring-boot-engineering` | `wechat-storage-acceptance` |
| Dockerfiles, Compose, gateway, env contract, runbooks | — | `deployment-operations` (deploying needs separate authorization) | `wechat-runtime-verification` |
| Unexplained failure, flaky or failing test | — | `debugging` (root cause), then the owning implementation skill | regression test via `testing` |
| Repository structure | `architecture-review` → `refactor-plan` | `refactoring` | `testing` |
| Branches, commits, PRs | — | `git-workflow` | `git-workflow` diff review |

Dependency order follows the actual dependency graph. Typical constraints: agree the contract before parallel server and client work; plan migrations before code that relies on the new schema; settle backend authorization before finalizing frontend assumptions; check realtime integration whenever messaging or notifications change.

PLAN also names the REVIEW checkpoints. Use one only after a contract, schema, authorization, or realtime-semantics increment, or after the first increment of a cross-layer change. PLAN decides whether the task must be split.

#### Mandatory routing record

For every task, PLAN must:

1. Map the request to the affected components and layers from SCOUT evidence: frontend, API contract, backend, security, database, realtime, storage, infrastructure, and tests, as applicable.
2. Select the smallest sufficient set of specialists based on actual dependencies and risks.
3. Record each selected skill with its purpose, the stage where it applies, and the supporting evidence.
4. Name relevant skills that were not selected when their omission could be questioned, and say why they are unnecessary.
5. Define the dependency order between specialists. For cross-layer work, settle shared contracts and ownership before implementation runs in parallel.
6. Map every acceptance criterion to its implementation owner and its verification procedure.
7. List the review checkpoints and every action that needs explicit user approval.

Add one entry per affected layer or component to the PLAN handoff:

```markdown
## Specialist routing
- Affected layer/component:
- Evidence from SCOUT: `path:symbol` or observed behavior
- Selected skill:
- Purpose and stage:
- Dependencies:
- Verification skill and evidence:
- Relevant skill considered but not selected:
- Reason for exclusion:
```

Routing rules:

- Frontend-only changes do not automatically need backend implementation skills, and backend changes do not automatically need frontend implementation skills.
- Cross-layer changes include every layer whose code or contract is affected, plus the matching integration verification (`frontend-integration-review`, `testing`).
- Consider authentication, authorization, schema, migration, realtime, storage, and infrastructure risk explicitly, even when the visible change looks small. Record "not affected" with evidence instead of omitting it.
- A skill listed in the plan does not count as applied until its `SKILL.md` has been read and followed in the stage that uses it.
- Do not claim automatic skill or agent invocation unless it was verified in the current environment. In this repository, handoffs are manual.
- If repository evidence cannot establish the affected layers, return to SCOUT instead of guessing.

COOK does not begin until PLAN has produced four things, and the user has approved them:

1. a dependency-ordered implementation plan;
2. the specialist routing record;
3. the verification matrix mapping each acceptance criterion to its owner and its check;
4. the list of approval gates.

### COOK entry: Git

Before the first edit, run the `git-workflow` preflight: branch, working tree, and diffs. Follow the existing branch naming in the repository (for example `add-*`, `update-*`, `refactor/*`). Create or switch branches only when the user authorizes it. Preserve unrelated and untracked work. Do not commit, push, or rebase without authorization for that action.

### TEST versus REVIEW

- **TEST** answers "does the change satisfy the acceptance criteria?" It runs checks and reports their results.
- **REVIEW** answers "what is weak, risky, or missing in this implementation?" It reads the diff independently, looks for security, data integrity, contract, concurrency, performance, UX, and maintainability problems that passing tests do not exclude, and checks plan conformance and scope drift. A green TEST report is input to REVIEW, not a verdict.

### Findings, remediation, and re-verification

1. REVIEW records each finding with severity, confidence, evidence, origin, remediation, verification, and owner (`continuous-improvement-review` format).
2. A Critical finding is surfaced immediately and stops further implementation.
3. An in-scope finding returns to COOK once fixing it is approved (`cook/references/review-findings.md`: verify, fix or defend with evidence, one fix at a time), then to TEST for the affected checks, then to REVIEW for the fix delta only.
4. A finding that changes requirements, a public contract, schema, security boundary, or file scope returns to PLAN (or BA if behavior is undecided).
5. Pre-existing, unrelated issues become recorded follow-ups. They do not block the change.
6. Stop when acceptance criteria are verified and no Critical/High finding remains open. Otherwise, after two cycles that fail to close a finding, report the blockers and return to PLAN.

### Proportionate improvement checks

- **COOK:** run focused local checks at each coherent increment and the planned checkpoint reviews. Record new weaknesses in the handoff. Do not run a full review after trivial edits.
- **TEST:** report missing cases and unverified assumptions as coverage gaps.
- **REVIEW:** run one independent final review and consolidate the open findings.

Every stage ends with: what was completed, evidence and checks, issues found, recommended remediation, next stage or Skill, and required approvals. For `plan` and `refactor-plan`, write the full report in Vietnamese in the saved plan artifact, including checks, specialist routing, approval gates, and handoff; the chat response contains only its path and one concise Vietnamese sentence with the recommended next step.

## Standard task lifecycle

These gates apply inside the stages above. Gates 0–2 belong to SCOUT/BA/PLAN, Gates 3–4 to COOK, Gate 5 to TEST, and Gate 6 to every stage's handoff.

### Gate 0 — Load context

Đọc:

1. `.agents/projects/wechat-clone/PROJECT-CONTEXT.md` when broader context is useful
2. current user task; historical prompts are reference material unless explicitly activated
3. các file được prompt yêu cầu
4. `git status` và các thay đổi đang có

Không overwrite hoặc revert thay đổi có sẵn của user.

### Gate 1 — Inspect the real application repository

Agent phải xác định:

- application repository nằm ở đâu;
- backend/frontend boundaries;
- startup/build/test commands;
- routes/controllers/services/repositories liên quan;
- Docker/Compose service names và replica model;
- các file test và docs đã được đọc.

Nếu đang ở `agency-agents` nhưng không có source WeChat, không được giả vờ đã kiểm tra implementation. Hãy tạo prompt/context hoặc báo cần application repository để thực thi.

### Gate 2 — Convert request into a task contract

Mỗi task phải ghi rõ:

```text
Task ID:
Objective:
In scope:
Out of scope:
Preconditions:
Files/components to inspect:
Acceptance criteria:
Evidence required:
Safety constraints:
```

Nếu endpoint, DTO, service name hoặc status chưa biết, agent phải tìm trong source trước khi dùng.

### Gate 3 — Baseline before mutation

Trước khi sửa code:

- chạy test/health check nhỏ nhất có thể;
- lưu kết quả pass/fail/skipped;
- xác định runtime state;
- kiểm tra `git status` và diff hiện tại;
- không đưa secret vào evidence.

### Gate 4 — Minimal implementation

Chỉ sửa khi có failure hoặc yêu cầu rõ ràng. Mỗi thay đổi phải có:

1. symptom;
2. root cause;
3. minimal fix;
4. regression test;
5. test rerun.

Không refactor hoặc đổi architecture chỉ vì agent thích một thiết kế khác.

### Gate 5 — Verification

Chạy test trực tiếp trước, rồi regression suite có liên quan. Phân loại kết quả:

- `PASS`: có output/evidence trực tiếp;
- `FAIL`: code/test chạy nhưng sai;
- `BLOCKED`: dependency hoặc runtime không thể dùng an toàn.

Một test bị skip không tự động là pass.

### Gate 6 — Handoff/report

Final report phải bắt đầu bằng verdict và sau đó nêu:

- những gì đã kiểm tra;
- những gì thực sự thay đổi;
- command/test output quan trọng;
- evidence cho mỗi acceptance criterion;
- limitation hoặc blocker;
- bước tiếp theo nhỏ nhất.

Riêng handoff của `plan` và `refactor-plan`: ghi báo cáo đầy đủ bằng tiếng Việt vào `docs/plans/YYYY/MM/DD/<short-task-slug>.md`, gồm cả kiểm tra, specialist routing, approval gates và manual handoff. Trong chat chỉ đưa liên kết đến file và một câu ngắn bằng tiếng Việt nêu bước tiếp theo; không chép hoặc tóm tắt bất kỳ phần nào của kế hoạch.

## R16 execution pattern

Khi task là R16, dùng thứ tự:

```text
Code audit
  → Compose baseline
  → identify replica A/B
  → User A upload
  → Supabase Storage + PostgreSQL verification
  → User A download via B
  → stop A, download via B
  → User B authorization denial
  → invalid-file validation
  → S3 storage failure behavior
  → frontend/backend regression
  → final evidence report
```

R16 chỉ đạt khi cross-instance bytes, authorization và instance independence đều có bằng chứng.

## Prompt quality gate

Trước khi dùng một prompt mới:

- có version và ngày cập nhật;
- có output format;
- có Definition of Done;
- có ít nhất một happy path, một edge case và một blocker/failure case;
- không dùng các từ mơ hồ như “kiểm tra kỹ”, “tối ưu tốt”, “đảm bảo production-ready” nếu không có tiêu chí đo được;
- không yêu cầu agent giả định endpoint, secret, runtime state hoặc test result.

## Handoff format

Dùng format ngắn sau giữa các agent:

```markdown
# Task Handoff

## Task
- ID:
- Owner:
- Reviewer:
- Scope:
- Stage / status: SCOUT | BA | PLAN | COOK | TEST | REVIEW — in progress | complete | blocked | returned to <stage>

## Acceptance criteria
- AC-n — criterion (source: BA, plan, or user request)

## Specialists
- `skill-name` — reason selected (PLAN onward: use the full specialist routing record)

## Decisions and assumptions
- decision or `ASSUMPTION:` statement

## Evidence so far
- [PASS/FAIL/SKIPPED/BLOCKED/INCONCLUSIVE] criterion — evidence

## Files inspected/changed
- `path/to/file` — reason

## Open findings
- CIR-n [Severity/Confidence] title — status, owner; or `none`

## Remaining work
- concrete next action

## Risks/blockers
- concrete blocker or `none`

## Next
- Recommended stage or Skill — prerequisites and approvals needed
```

Fill only the sections that apply. Each stage updates the previous handoff instead of restating it.

## Current task handoff — TEMP-AUTH-EMAIL-OFF-2026-10-20

- **Giai đoạn / trạng thái:** COOK — kế hoạch gốc P1–P6 hoàn tất cho mã local; kế hoạch sửa đổi `TEMP-AUTH-EMAIL-OFF-2026-10-20-R1` P1–P4 `DONE`; P7 `BLOCKED` chờ phê duyệt riêng cho Render.
- **Phạm vi:** Chế độ tắt email xác thực tạm thời; không thay đổi schema, Render, Vercel hoặc dịch vụ bên ngoài.
- **Specialists:** `api-contract-design` chốt hợp đồng; `spring-boot-engineering`, `backend-security`, `frontend-engineering` triển khai; `debugging` xác nhận nguyên nhân Vitest; `testing`, `frontend-quality-gate` xác minh; `continuous-improvement-review` rà soát diff.
- **Quyết định:** `AUTH_EMAIL_ENABLED` mặc định bật; khi tắt, đăng ký không tạo token email; quên/gửi lại trả `503` tổng quát; việc login vẫn do `AUTH_REQUIRE_EMAIL_VERIFIED` điều khiển. Response đăng ký có `verificationEmailSent`.
- **Nguyên nhân đã xác nhận:** `beforeEach(() => mock.mockReset())` trả về mock vì `mockReset()` trả chính nó; Vitest đăng ký hàm trả về làm cleanup rồi gọi lại sau test. Mock test quên mật khẩu ném lỗi khi cleanup chạy nên test fail sau khi các assertion UI đã qua. Đã đổi cả hai test page sang callback block-body không trả giá trị.
- **Bằng chứng kiểm tra:** `STORAGE_TYPE=local mvn -f wechatbackend/pom.xml -Dtest=AuthEmailConfigurationTest,AuthApiIntegrationTest,AuthEmailDisabledApiIntegrationTest,AuthServiceTest test` — 27/27 pass; `npm test -- src/pages/RegisterPage.test.jsx src/pages/ForgotPasswordPage.test.jsx` — 4/4 pass; ESLint hai page và hai test pass; `npm run build` pass; `git diff --check` pass. Baseline của ForgotPasswordPage test fail trước khi sửa với `Service unavailable`; sau sửa pass. Test backend dùng H2 và log có warning Redis cache invalidation do Redis không sẵn trong test; không có assertion fail.
- **Review:** `continuous-improvement-review` read-only trên diff auth/API/UI/test không thấy Critical/High/Medium finding còn mở. P2 mở rộng cùng fix block-body sang `RegisterPage.test.jsx` sau khi phát hiện cùng pattern cleanup; nằm trong test scope P4 của kế hoạch gốc, không đổi production behavior. Diagnostics và test tạm đã xóa.
- **Blocker / giới hạn:** Không có blocker cho mã local. Runtime Render/Vercel chưa xác minh; không thay đổi dashboard. P7 cần phê duyệt riêng.
- **Bước tiếp:** Chuyển `TEST` với `testing` và `frontend-quality-gate` để xác nhận AC. Sau TEST/REVIEW, chỉ thực hiện P7 khi được người dùng phê duyệt rõ ràng.

## Current task handoff — WEB-SETTINGS-SECURITY-THEME

- **Task / owner / reviewer:** Settings, dark mode và đổi mật khẩu; owner COOK `wechat_project_lead`; reviewer dự kiến `wechat_code_reviewer` qua `continuous-improvement-review`.
- **Stage / status:** COOK — P1 `DONE`; P2 `DONE`; P3 `DONE_WITH_CONCERNS`; P4 `BLOCKED` chờ TEST và REVIEW theo handoff thủ công.
- **Phạm vi:** `/app/settings`, theme localStorage trên toàn website gồm auth, frontend form gọi endpoint đổi mật khẩu hiện có. Không thay đổi backend, API contract, database, realtime, deployment.
- **Acceptance evidence:** SET-01 được test guest redirect và authenticated route; THEME-02 được test load/reload/persistence; theme control và sidebar được test; password form/API được test cho mismatch, lỗi, pending, payload, session clear và redirect. THEME-01/03 chưa có kiểm tra browser trực quan, tương phản và bàn phím.
- **Specialists:** `frontend-design-system`, `frontend-ui-ux`, `frontend-engineering`, `wechat-frontend-integration` đã đọc và áp dụng trong COOK. `frontend-quality-gate`, `testing`, `frontend-integration-review`, `continuous-improvement-review` để handoff thủ công; chưa chạy.
- **Files changed:** `wechatfrontend/src/main.jsx`, `src/styles-tokens.css`, `src/index.css`, `src/App.jsx`, `src/components/conversations/ChatSidebar.jsx`, `src/services/api/authApi.js`; thêm `src/features/settings/settingsStore.js` và test, `src/pages/SettingsPage.jsx` và test, `src/components/conversations/ChatSidebar.test.jsx`, `src/components/settings/ChangePasswordForm.jsx` và test, `src/services/api/authApi.test.js`.
- **Checks:** baseline `npm run lint`, `npm run build`, auth store/action tests — PASS (4 tests); COOK feature suite `npm test -- src/features/settings/settingsStore.test.js src/pages/SettingsPage.test.jsx src/components/conversations/ChatSidebar.test.jsx src/components/settings/ChangePasswordForm.test.jsx src/services/api/authApi.test.js` — PASS (11 tests); `npm run lint`, `npm run build`, `git diff --check` — PASS; `mvn -f wechatbackend/pom.xml -Dtest=AuthApiIntegrationTest test` — PASS (13 tests, H2; có cảnh báo Redis cache vì Redis không chạy).
- **Command deviation:** `wechatbackend/mvnw` không chạy được vì thiếu `.mvn/wrapper/maven-wrapper.properties`; cùng test được chạy thành công bằng system Maven. Lượt Maven trong sandbox trước rerun bị lỗi khởi tạo context khi SDK timeout/socket bị chặn; lần chạy ngoài sandbox PASS.
- **Runtime / visual:** Compose chưa khởi động do các biến bắt buộc chưa được cấu hình; `.env` không được đọc/sửa. Browser, mobile viewport, contrast, keyboard và screen reader — `NOT RUN`, giao cho TEST ghi evidence hoặc blocker.
- **Pre-existing changes:** giữ nguyên các thay đổi auth-email, `WORKFLOW.md`/skill edits, `.aws` deletion và các file untracked có trước; không stage/commit.
- **Out of scope / risks:** badge trạng thái STOMP chập chờn chưa được điều tra/sửa; yêu cầu `debugging` riêng. API client hiện thử refresh với 401; UX lỗi mật khẩu sai cần integration review, không đổi interceptor trong COOK.
- **Open findings:** chưa có REVIEW độc lập; không ghi nhận Critical/High finding trong COOK.
- **Next:** TEST với `testing`, `frontend-quality-gate`, `frontend-integration-review`; sau đó REVIEW. Cần người dùng chọn handoff thủ công.
