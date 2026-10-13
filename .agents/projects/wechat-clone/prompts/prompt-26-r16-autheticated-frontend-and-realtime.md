# Prompt 26 — R16 Authenticated Frontend and Realtime Runtime Acceptance

Version: v1.0
Date: 2026-10-13
Project: WeChat Clone / Real-Time Web Chat System
Status: Ready for explicit selection; do not execute automatically

Primary agent: wechat_project_lead
Reviewer: wechat_code_reviewer
Supporting skills:
- wechat-flow-trace
- wechat-frontend-integration
- wechat-realtime-contract
- wechat-runtime-verification

## Objective

Use disposable authenticated users against the live Compose gateway to execute the skipped Playwright scenarios and prove that browser login/logout and two-user realtime messaging work through the current frontend, REST, STOMP, Redis, and backend-replica topology.

## Current context

Prompt 25 established:

- Node.js/npm are available through an interactive login shell with NVM.
- Vitest, ESLint, and the production build pass.
- The public Playwright SPA smoke test passes.
- The authenticated Playwright tests were skipped because the required `E2E_*` variables were unset.
- Supabase cross-replica storage acceptance was already exercised.
- No application source or environment files were changed.

Historical results are not runtime proof. Reconfirm all relevant contracts from current source and runtime state.

Read first:

- `.agents/projects/wechat-clone/PROJECT-CONTEXT.md`
- `.agents/projects/wechat-clone/WORKFLOW.md`
- `.agents/projects/wechat-clone/AGENT-BASE-PROMPT.md`
- `wechatfrontend/package.json`
- `wechatfrontend/package-lock.json`
- `wechatfrontend/playwright.config.js`
- `wechatfrontend/e2e/smoke.spec.js`
- frontend auth store/actions and login page
- frontend API client
- frontend WebSocket/STOMP manager
- chat page, conversation store, and message store
- backend authentication endpoints
- backend conversation/message endpoints
- current Compose and gateway configuration
- existing auth, STOMP, Redis, and runtime tests

## Scope

### In scope

- Trace the current login/logout browser flow.
- Trace authenticated REST and STOMP initialization.
- Provision two disposable users using supported application APIs.
- Provision or identify one conversation shared by both disposable users.
- Inject credentials only into the current Playwright process.
- Run the authenticated Playwright tests against the live gateway.
- Verify that one browser-originated message is received by the second browser.
- Run relevant frontend and realtime regressions.
- Produce evidence-based PASS, FAIL, or BLOCKED results.

### Out of scope

- Changing application behavior merely to make tests pass.
- Reworking R11 auth-state caching.
- Reworking Redis Pub/Sub or STOMP architecture.
- Repeating the complete Supabase storage acceptance.
- Editing `.env`, `.env.example`, credentials files, or lockfiles.
- Creating public endpoints, debug headers, authentication bypasses, or permissive CORS.
- Deleting Docker volumes, resetting databases, or stopping unrelated containers.

## Safety constraints

- Use only disposable users and disposable conversation/message data.
- Never print passwords, access tokens, cookies, authorization headers, or signed URLs.
- Do not persist `E2E_*` credentials in `.env` or repository files.
- Use shell-scoped or process-scoped variables only.
- Preserve existing containers, volumes, databases, and unrelated fixtures.
- Clean up only disposable users, conversations, and messages created by this task where supported.
- If cleanup is unsafe, record the identifiers privately and report the limitation without exposing them.
- Do not use MinIO as a substitute for the live frontend/runtime path.

## Phase 1 — Inspect before acting

Confirm from current source:

1. The actual frontend package manager and lockfile.
2. The exact package scripts.
3. The Playwright base URL and web-server behavior.
4. Required `E2E_*` variable names.
5. The current login, logout, conversation, message, and STOMP contracts.
6. The supported API path for creating disposable users and a shared conversation.
7. The active Compose gateway and backend replica topology.

Do not invent endpoint names, DTOs, environment variables, or status values.

## Phase 2 — Shell and baseline diagnostics

Run the following in the same login-shell form used for validation:

```bash
bash -lic '
command -v node
command -v npm
node --version
npm --version
printf "%s\n" "$PATH"
'
Record whether the shell is:
- interactive or non-interactive;
- login or non-login;
- using NVM;
- running inside Docker or directly on the host.
Inspect package files before installing anything:
find . -maxdepth 1 -type f \
  \( -name "package.json" -o -name "package-lock.json" \
     -o -name "yarn.lock" -o -name "pnpm-lock.yaml" \
     -o -name "npm-shrinkwrap.json" \) -print

node -e 'console.log(require("./package.json").scripts)'
Use the existing lockfile. Do not run npm install.
If dependencies are already present, use them. If installation is genuinely required, use only the repository-authoritative command such as:
npm ci --ignore-scripts
Record actual results.
Run the baseline:
npm test
npm run lint
npm run build
git diff --check
Phase 3 — Runtime and disposable fixture setup
Confirm the live gateway and required services are healthy using existing Compose evidence.
Create two disposable users:
- User A: sender and owner of the test conversation.
- User B: receiver and unrelated test principal.
Create or identify one conversation in which both users are members.
Do not print credentials or tokens. Record only redacted fixture labels and non-sensitive IDs where necessary.
Set these values only in the process executing Playwright:
E2E_BASE_URL
E2E_EMAIL
E2E_PASSWORD
E2E_USER_A_EMAIL
E2E_USER_A_PASSWORD
E2E_USER_B_EMAIL
E2E_USER_B_PASSWORD
E2E_CONVERSATION_ID
Do not write these values to any repository or environment file.
If supported fixture provisioning is unavailable, mark the authenticated cases BLOCKED; do not reuse personal credentials or infer success.
Phase 4 — Authenticated Playwright execution
Run the existing script without modifying Playwright configuration:
npm run test:e2e
The expected authenticated coverage is:
1. User A can log in, reach /app, see the chat shell, and log out.
2. User A and User B can authenticate in separate browser contexts.
3. Both users can open the same conversation.
4. User A can send one message.
5. User B receives exactly one matching message within the configured timeout.
6. Both browser contexts close cleanly.
If Chromium fails before test execution because of the execution sandbox:
- classify it as an environment/runner failure;
- rerun the same unchanged command with approved browser host permission if available;
- do not modify application code, Playwright configuration, or browser security flags.
A skipped test is not a pass.
Phase 5 — Realtime and contract verification
Verify, using source and runtime evidence:
- login creates the expected authenticated frontend state;
- logout clears the expected client state;
- STOMP connects only after authentication;
- the subscription destination matches the backend contract;
- the message is sent through the supported REST/STOMP path;
- the receiving browser displays one message, not duplicates;
- reconnect or initial synchronization does not erase the delivered message;
- authorization failures do not expose conversation data.
Run focused realtime/frontend tests identified during source tracing.
Acceptance criteria
ID	Criterion	Required evidence
R16-F1	Correct Node/npm environment is used	shell flags, PATH, executable paths, versions
R16-F2	Existing package manager/lockfile is honored	package files and install decision
R16-F3	Frontend unit tests remain green	Vitest output
R16-F4	Lint and production build remain green	command exit/output
R16-F5	Public SPA smoke test passes	Playwright result
R16-F6	Disposable User A logs in and logs out	authenticated Playwright result
R16-F7	Two authenticated browsers open one shared conversation	Playwright result
R16-F8	User A’s message reaches User B exactly once	message content redacted or fixture-labelled, timing/result
R16-F9	STOMP/realtime authorization contract is preserved	source and runtime evidence
R16-F10	No credentials or environment files are exposed or changed	status/diff/security inspection
R16-F11	Disposable fixtures are cleaned up or limitation is documented	cleanup evidence
R16-F12	No unrelated Docker state is changed	Compose/status evidence


Failure policy
For every failure:
1. Classify it as environment, browser runner, fixture, frontend, REST, STOMP, Redis, authorization, or test failure.
2. Record the exact command and observable error.
3. Do not call a skipped test passed.
4. Implement a fix only if it is reproducible, directly in scope, security-preserving, and authorized by this prompt.
5. Add or update a focused regression test for any fix.
6. Rerun the affected check and related regressions.
Regression commands
bash -lic 'npm test'
bash -lic 'npm run lint'
bash -lic 'npm run build'
bash -lic 'npm run test:e2e'
git diff --check
git status --short --untracked-files=all
Final report format
# Verdict

PASS / FAIL / BLOCKED

## Environment diagnosis

- Shell:
- Node path/version:
- npm path/version:
- PATH/NVM finding:
- Docker/sudo/alternate-runtime finding:

## Fixture and runtime setup

- Gateway:
- Disposable users:
- Shared conversation:
- Cleanup result:

## Acceptance matrix

| ID | Status | Evidence |
|---|---|---|
| R16-F1 | PASS/FAIL/BLOCKED | ... |
| R16-F2 | PASS/FAIL/BLOCKED | ... |
| R16-F3 | PASS/FAIL/BLOCKED | ... |
| R16-F4 | PASS/FAIL/BLOCKED | ... |
| R16-F5 | PASS/FAIL/BLOCKED | ... |
| R16-F6 | PASS/FAIL/BLOCKED | ... |
| R16-F7 | PASS/FAIL/BLOCKED | ... |
| R16-F8 | PASS/FAIL/BLOCKED | ... |
| R16-F9 | PASS/FAIL/BLOCKED | ... |
| R16-F10 | PASS/FAIL/BLOCKED | ... |
| R16-F11 | PASS/FAIL/BLOCKED | ... |
| R16-F12 | PASS/FAIL/BLOCKED | ... |

## Tests actually run

- `command` — actual result
- `command` — actual result

## Files changed

- `path` — reason
- `none` if no files changed

## Limitations and blockers

- Explicit skipped tests
- Browser runner limitations
- Missing fixture prerequisites
- None, if applicable

## Recommended next action

- One smallest concrete next step.
Definition of Done
This task is complete only when:
- authenticated Playwright tests execute with disposable credentials;
- login/logout succeeds;
- two browser contexts share the expected conversation;
- one message is delivered exactly once through the realtime path;
- frontend unit, lint, build, and applicable realtime checks are recorded;
- skipped, failed, and blocked cases are distinguished accurately;
- no application source or environment files are changed unnecessarily;
- disposable fixtures are cleaned up or documented;
- the final report contains direct evidence for every acceptance criterion.
Prompt QA cases
1. Happy path: both users authenticate and the realtime message is delivered exactly once.
2. Edge case: one authenticated browser reconnects or reloads without duplicate message rendering.
3. Failure case: credentials, browser capability, gateway, or STOMP service is unavailable and the report correctly states BLOCKED rather than inferring success.
Changelog
v1.0 — 2026-10-13
- Created the next scoped task after Prompt 25.
- Targets the remaining authenticated Playwright and browser-realtime validation gap.
- Adds explicit shell/NVM diagnosis, disposable fixture handling, and no-.env credential injection rules.