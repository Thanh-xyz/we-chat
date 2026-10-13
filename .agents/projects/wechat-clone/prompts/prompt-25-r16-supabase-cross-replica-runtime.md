# Prompt 25 — R16 Supabase Storage Cross-Replica Runtime Acceptance

- Version: v1.0
- Date: 2026-10-13
- Project: WeChat Clone
- Primary agent: wechat_project_lead
- Supporting skills: wechat-storage-acceptance, wechat-runtime-verification, wechat-flow-trace, wechat-frontend-integration
- Reviewer: wechat_code_reviewer
- Status: Ready for explicit selection; do not execute automatically

## Objective

Execute the current R16 runtime acceptance for the production storage boundary.

Prove with direct evidence from the running Compose stack that:

- User A can upload and finalize a valid attachment through the supported gateway flow;
- the bytes are stored in the private Supabase S3-compatible bucket and represented correctly in PostgreSQL;
- a different backend replica can authorize and serve the same attachment;
- the attachment remains usable when the original replica is stopped;
- User B cannot access or delete User A's attachment;
- invalid, missing, and failed uploads do not become READY;
- frontend, credential, private-bucket, cleanup, and regression contracts remain safe.

This is an acceptance and verification task. Implement only the smallest R16-specific fix when a reproducible defect is found. Do not redesign storage or revisit the completed R11 auth-state cache work.

## Current context

Prompt 24/R11 has been implemented and validated with unit, Redis, and PostgreSQL integration evidence. The next unresolved production-readiness risk is the Supabase-backed attachment lifecycle across backend replicas.

The expected current contract is:

1. The backend authenticates the user and creates a presigned upload request.
2. The browser uploads bytes directly to private Supabase S3-compatible storage.
3. The backend completes the upload only after checking object existence, size, magic bytes, SHA-256, and image metadata where applicable.
4. A READY attachment is available only through authorized application access.
5. A second backend replica uses shared PostgreSQL metadata and shared Supabase storage, without process-local state.
6. Legacy MinIO support, if present, is a rollback or migration boundary, not proof of the current production runtime.

MinioObjectStorageIT remains useful for generic S3 behavior, but it does not prove Supabase credentials, private-bucket policy, gateway routing, cross-replica behavior, or production authorization.

Re-read the repository before acting. Re-verify endpoint names, service names, container names, ports, health checks, environment variables, and routing from current source.

## Scope

### In scope

- Trace the attachment contract from React frontend through gateway, backend, database, and Supabase storage.
- Verify Compose health with PostgreSQL, Redis, two backend replicas, frontend, and gateway.
- Exercise upload, completion, download, authorization, deletion, and cleanup.
- Prove cross-replica access and independence.
- Verify private storage and browser credential boundaries.
- Exercise invalid/incomplete objects and one safe bounded provider failure where possible.
- Run supported backend and frontend regressions.
- Produce an evidence-based PASS, FAIL, or BLOCKED report.

### Out of scope

- Making the bucket public or weakening authorization.
- Committing credentials, tokens, signed URLs, or environment values.
- Replacing Supabase with MinIO for convenience.
- Destructive Docker cleanup, volume deletion, database reset, or broad data purge.
- Reworking R11, Redis Pub/Sub, STOMP delivery, or unrelated frontend features.
- Broad refactoring, load testing, or performance tuning.
- Editing .env files. If an environment override is required, report the exact variables and safe injection method instead of changing environment files without explicit authorization.

## Safety and evidence rules

- Inspect and preserve the existing working tree before making changes.
- Never print secrets, passwords, access tokens, signed URLs, authorization headers, or complete environment files.
- Redact private object keys, user identifiers, internal hostnames, and provider details in the final report.
- Do not add debug endpoints, public ports, permissive CORS, public buckets, or authentication bypasses.
- Use only disposable users and fixtures.
- Do not run docker compose down -v, prune commands, or destructive database/storage operations.
- Keep runtime changes reversible and clean up fixtures safely.
- If live Supabase credentials, the stack, or a required dependency is unavailable, report BLOCKED. Do not substitute MinIO and report a Supabase pass.
- Use hashes and metadata instead of copying attachment bytes into logs.

## Required source-trace phase

Read before changing code or running the full acceptance:

- .agents/projects/wechat-clone/PROJECT-CONTEXT.md
- .agents/projects/wechat-clone/WORKFLOW.md
- .agents/projects/wechat-clone/AGENT-BASE-PROMPT.md
- .agents/projects/wechat-clone/prompts/README.md
- docs/architecture/storage.md
- docs/api/attachments.md
- docs/frontend.md
- docs/deployment.md
- docs/testing.md
- docker/docker-compose.yml and referenced Compose/Nginx files
- backend AttachmentController, AttachmentService, FileStorageService, active S3 adapter, attachment repository, security configuration, cleanup job, and the V19 Flyway migration
- frontend attachment API/client and authenticated upload/download UI
- attachment/storage tests, MinioObjectStorageIT, runtime tests, and Playwright coverage

Use rg to identify actual classes, endpoints, profiles, environment variables, health checks, test fixtures, object-key rules, and replica routing. Record source paths for every contract asserted in the report.

Re-verify:

- presigned upload creation;
- browser direct upload;
- completion/finalization;
- authorized download URL or response;
- legacy download, if retained;
- deletion;
- message/conversation attachment references;
- ownership checks;
- PENDING/READY/FAILED transitions;
- object-key and provider selection;
- orphan/incomplete cleanup;
- gateway routing to backend replicas.

## Phase 1 — Baseline and topology

Run read-only checks first, using the actual current Compose files:

    git status --short
    git branch --show-current
    git diff --check
    docker compose -f <current-compose-files> config
    docker compose -f <current-compose-files> ps

Do not print environment files. Confirm the gateway is the supported entry point, two backend replicas are running and independently identifiable through existing operational evidence, PostgreSQL and Redis are healthy, and Supabase rather than MinIO is the active runtime adapter.

Run supported regressions, adapting only paths or profiles confirmed by source:

    cd wechatbackend && mvn test
    cd wechatbackend && mvn verify -Ppostgres-integration
    cd wechatbackend && mvn verify -Ps3-integration
    cd wechatfrontend && npm test
    cd wechatfrontend && npm run lint
    cd wechatfrontend && npm run build
    git diff --check

If the Maven wrapper is broken because wrapper metadata is missing, use installed Maven and record that tooling limitation separately.

Do not add replica identity endpoints or debug headers. Use current gateway logs, container metadata, or existing routing behavior.

## Phase 2 — Controlled fixtures

Create two disposable authenticated users:

- User A: owner/uploader.
- User B: unrelated user without access.

Prepare:

- valid small fixture within documented limits;
- empty fixture;
- misleading extension/MIME fixture;
- malformed image or invalid magic-byte fixture;
- oversized fixture if safely generatable.

Compute the valid fixture SHA-256 locally. Record only a fixture label, digest, size, and MIME type. Correlate tests using attachment IDs without logging credentials or full signed URLs.

## Phase 3 — Cross-replica Supabase acceptance

Use the supported gateway/client flow and establish which replica handles each step using existing evidence.

### A. Upload and finalize through replica A

As User A:

1. Request the presigned upload.
2. Confirm the response does not disclose storage credentials or internal service credentials.
3. Upload bytes to the returned private Supabase S3-compatible target.
4. Complete the upload through the gateway.
5. Confirm completion requires an existing, valid object.
6. Record attachment ID, READY state, size, MIME type, and source digest.

Use safe provider metadata or HEAD/stat evidence to prove the object is in Supabase, not merely in a local filesystem or MinIO volume.

### B. Authorize and download through replica B

Through a different backend replica:

1. Request the authorized download URL or response as User A.
2. Fetch the content through the supported path.
3. Compute the downloaded SHA-256 and compare with the source.
4. Verify response type, size, and authorization.
5. Confirm no process-local storage or metadata is required.

If the gateway cannot pin traffic safely, use the least invasive existing routing/container method and document how replica identity was established.

### C. Stop-replica independence

Stop or isolate only replica A using a reversible, project-supported Compose operation. Keep PostgreSQL, Redis, Supabase, gateway, frontend, and replica B available.

Through the gateway and as User A:

- obtain or refresh download authorization;
- download the attachment;
- compare digest and metadata;
- confirm the attachment remains READY and accessible.

Restore the expected replica count safely. Do not delete volumes or reset databases.

### D. Authorization matrix

As User B, verify denial through every supported path:

- download URL issuance;
- legacy download, if retained;
- direct or guessed attachment access;
- deletion;
- message/conversation reference access where applicable.

As User A, verify owner access still succeeds. Confirm authorization is enforced by the backend and not by a public object URL. Record statuses and safe response categories only.

### E. Delete and cleanup

If lifecycle rules allow, delete as User A and verify:

- database state matches the documented contract;
- object deletion/tombstoning follows the contract;
- User B remains denied;
- unrelated objects are unaffected;
- asynchronous cleanup completes within its documented bound.

## Phase 4 — Negative and failure acceptance

Use the normal presigned-upload and completion flow where possible. Verify these never become READY:

- empty content;
- wrong magic bytes;
- malformed image;
- misleading MIME type or extension;
- unsupported type;
- oversized object;
- missing object at completion;
- declared size or checksum mismatch.

Record status/error category, resulting attachment state, object cleanup/quarantine behavior, retry safety, and absence of provider details or credentials.

Exercise one safe bounded provider failure if it can be isolated without damaging shared infrastructure. If not, mark it not executed with the reason. Unit tests alone do not prove live provider failure handling.

## Phase 5 — Security and frontend contract

Verify from source and runtime evidence:

- bucket is private and anonymous object access is denied;
- signed URLs are short-lived and scoped, where used;
- frontend receives only the intended presigned URL and never storage credentials;
- no frontend bundle contains Supabase secret keys or backend-only credentials;
- browser does not require internal storage hostnames beyond the intended presigned request;
- CORS and methods match the direct-upload contract;
- gateway preserves authentication and attachment authorization;
- frontend success/error handling matches completion/download behavior;
- authorization failures do not expose private bytes.

Distinguish source evidence from runtime evidence when a property cannot be tested safely.

## Minimal implementation policy

Implement a fix only when the defect is reproducible, directly within R16, security-preserving, and covered by a focused regression test. Rerun all relevant verification after a change.

Do not modify R11 auth-state behavior, Redis realtime behavior, unrelated APIs, or production bypasses.

## Acceptance matrix

Complete this matrix with evidence references:

| ID | Acceptance | Required evidence |
|---|---|---|
| R16-A1 | Compose stack healthy with two backend replicas | status/health and timestamp |
| R16-A2 | Supabase private adapter active | redacted source/config and provider evidence |
| R16-A3 | User A creates a safe presigned upload | result category, no secrets |
| R16-A4 | Valid fixture finalizes READY | ID, state, size, digest |
| R16-A5 | Object exists in private Supabase | safe HEAD/stat evidence |
| R16-A6 | Different replica authorizes/downloads | replica evidence and matching digest |
| R16-A7 | Download survives original replica stop | failover evidence and matching digest |
| R16-A8 | User B denied on supported paths | status matrix |
| R16-A9 | Invalid/missing objects never READY | validation matrix |
| R16-A10 | Delete/cleanup matches contract | lifecycle evidence |
| R16-A11 | Browser/security boundary preserved | private access and bundle/config checks |
| R16-A12 | Regressions remain green | test/lint/build results |

Emulator-only results cannot satisfy R16-A2, R16-A5, R16-A6, or R16-A7.

## Required final report

Write the report in the task output or established evidence location. Include:

1. verdict: PASS, FAIL, or BLOCKED;
2. branch and initial working-tree state;
3. source-trace summary with file paths;
4. Compose topology and health;
5. replica identification method;
6. redacted Supabase/private-bucket evidence;
7. completed R16-A1 through R16-A12 matrix;
8. User A/User B authorization results;
9. valid/invalid fixture results;
10. delete and cleanup results;
11. frontend/security results;
12. commands and outcomes;
13. changed files, if any;
14. limitations, skipped cases, and exact blockers;
15. recommended next action.

Use PASS only with direct evidence for all required current-Supabase criteria. Use FAIL when the system was exercised and a criterion failed. Use BLOCKED when a prerequisite or safe path was unavailable. Never convert a missing Supabase prerequisite into an emulator pass.

## QA cases

### Happy path

- User A uploads a valid fixture through the gateway.
- Supabase stores it privately.
- Completion marks it READY.
- Replica B authorizes and downloads it.
- Download digest equals the source.
- Download remains successful after replica A stops.
- User B is denied.

### Edge cases

- completion before object existence;
- empty object;
- wrong magic bytes;
- malformed image;
- mismatched content type or extension;
- oversized object;
- stale/expired signed URL;
- repeated completion;
- repeated delete;
- attachment referenced by a message;
- abandoned pending upload cleanup.

### Failure cases

- Supabase HEAD/stat failure;
- Supabase GET/download failure;
- database lookup failure;
- one backend replica unavailable;
- gateway routing to the remaining replica;
- unauthorized access through every supported endpoint.

## Definition of Done

The task is complete only when the source contract and topology are documented; real private Supabase storage has been exercised; upload/finalize/download works across replicas and after the original replica stops; User B is denied; invalid and failure cases have bounded outcomes; frontend/security and regression evidence is recorded; and the report has a clear PASS, FAIL, or BLOCKED verdict.

## Changelog

- v1.0 (2026-10-13): Created the current R16 brief for private Supabase storage, cross-replica behavior, authorization, validation, cleanup, frontend integration, and evidence reporting.
