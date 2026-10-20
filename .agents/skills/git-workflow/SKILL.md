---
name: git-workflow
description: Manage Git workflows for the WeChat Clone repository, including branch strategy, status inspection, safe commits, synchronization with main, conflict resolution, pull requests, diff review, and rollback planning. Use when performing Git operations or preparing, reviewing, or integrating code changes in this repository.
---

# Git Workflow — WeChat Clone

## 1. Purpose

Manage Git operations safely and consistently across the WeChat Clone repository.

The repository contains a frontend, Spring Boot backend, Docker infrastructure, gateway configuration, and related deployment files. Git operations must preserve work across all these components.

Prioritize:
1. Preserving existing user work.
2. Understanding the current branch and working tree before acting.
3. Keeping changes focused and reviewable.
4. Verifying the result after every meaningful operation.
5. Avoiding accidental commits, pushes, merges, resets, or deletions.

## 2. Mandatory preflight

Before performing any Git operation that could change repository state:

1. Confirm the repository root with `git rev-parse --show-toplevel`.
2. Inspect the current branch with `git branch --show-current`.
3. Inspect the working tree with `git status --short --branch`.
4. Review relevant changes using `git diff` and `git diff --cached`.
5. Check for untracked files and files ignored by Git when relevant.
6. Inspect recent commits with `git log -5 --oneline --decorate`.
7. Determine whether the target branch and remote-tracking references exist.
8. Identify user changes that must be preserved.

Never assume the working tree is clean because a previous command reported it as clean.

Do not expose secret values when inspecting environment files or deployment configuration.

## 3. Read-only operations

Use read-only commands freely when relevant to the task:

- `git status`
- `git branch --list`
- `git branch -r`
- `git log`
- `git show`
- `git diff`
- `git diff --cached`
- `git remote -v`
- `git merge-base`
- `git rev-parse`
- `git ls-files`

Review command options before using them. Avoid commands that display credentials embedded in remote URLs or sensitive file contents.

When asked to explain repository history, identify the relevant commit hashes and changed paths. Do not modify the working tree merely to answer a question.

## 4. Branch strategy

Use descriptive branch names consistent with the existing repository conventions.

Suggested patterns, only when no established convention exists:

- `feature/<short-description>`
- `fix/<short-description>`
- `refactor/<short-description>`
- `test/<short-description>`
- `docs/<short-description>`
- `chore/<short-description>`

Before creating or switching branches:

1. Inspect the current branch and working tree.
2. Identify uncommitted and untracked work.
3. Confirm the intended base branch.
4. Check whether the destination branch already exists.
5. Explain any risk of losing access to the current work.

Do not automatically create a branch for every task if the user has requested work on the current branch.

Never overwrite an existing branch without explicit approval.

## 5. Synchronizing with main

When asked to synchronize a working branch with `main`:

1. Determine the current branch and whether the working tree is clean.
2. Inspect local and remote branch references.
3. Fetch remote updates only when network access and the requested operation permit it.
4. Compare the current branch with the intended base.
5. Explain the difference between merge and rebase when the choice matters.
6. Recommend a strategy based on the repository's existing conventions and whether commits have been shared.
7. Execute only the approved operation.
8. Verify the resulting history, working tree, and conflict status.

Never assume `origin` is the correct remote or that `main` is up to date.

Never force-push or rewrite shared history without explicit approval.

Do not automatically stash, reset, clean, or discard changes to make synchronization easier.

## 6. Commit workflow

When asked to prepare a commit:

1. Inspect the complete intended diff.
2. Identify unrelated, generated, sensitive, or accidental files.
3. Run appropriate tests when requested or when required to verify the change.
4. Stage only the intended paths.
5. Inspect the staged diff using `git diff --cached`.
6. Check for secrets, credentials, local environment files, build artifacts, and unrelated modifications; for example `git diff --cached | grep -nEi '(api[_-]?key|secret|password|token|BEGIN [A-Z ]*PRIVATE KEY)'`, reporting file and line only, never the matched value.
7. Propose a concise commit message that accurately describes the change.
8. Create the commit only when the user has authorized committing.
9. Verify the resulting commit and working tree.

Do not use `git add .` or `git add -A` blindly.

Do not amend an existing commit unless explicitly requested or clearly authorized.

Do not skip hooks, tests, or validation merely to make a commit succeed.

Never commit secrets, private keys, production credentials, or local environment files containing secrets.

## 7. Diff review and change isolation

When reviewing changes:

- Inspect both staged and unstaged diffs.
- Identify unexpected changes in frontend, backend, Docker, gateway, migrations, and CI configuration.
- Check for accidental line-ending changes, generated files, and unrelated formatting changes.
- Confirm that refactoring does not silently alter runtime behavior.
- Explain potential API, build, deployment, and configuration compatibility risks.
- Separate confirmed defects from possible risks.

Do not discard unrelated user changes, even if they appear unnecessary.

When a change is too broad, recommend splitting it into focused commits or pull requests.

## 8. Conflict resolution

When a merge or rebase conflict occurs:

1. Identify every conflicted path.
2. Inspect the surrounding code and both sides of the conflict.
3. Determine the intended behavior from repository context and tests.
4. Explain conflicts that require a product or architectural decision.
5. Resolve only conflicts whose intended outcome is sufficiently clear.
6. Preserve both sides' required behavior where appropriate.
7. Run relevant tests and inspect the final diff.

Do not resolve conflicts by blindly accepting `ours` or `theirs`.

Do not use `git checkout -- .`, `git restore .`, `git reset --hard`, or `git clean` to clear conflicts or recover from errors.

If a conflict could affect database migrations, authentication, authorization, WebSocket behavior, storage, or deployment, stop and request clarification when the intended behavior is uncertain.

## 9. Pull requests

When preparing a pull request:

- Confirm the source and target branches.
- Review the commits and complete diff.
- Summarize the purpose and scope.
- Document relevant tests and their actual results.
- Explain configuration, migration, and deployment implications.
- Identify known limitations and follow-up work.
- Check for secrets and unrelated files.
- Propose a clear PR title and description.

Do not create, approve, merge, or close a remote pull request unless the user has authorized that specific action and the necessary integration is available.

Do not claim CI has passed unless its result has been verified.

### Finishing a development branch

When implementation, TEST, and REVIEW are complete:

1. Rerun the relevant checks on the current branch state and report them; do not finish on failing or unrun checks without saying so.
2. Confirm the base branch (usually `main`) and summarize the branch diff against `git merge-base`.
3. Present exactly these choices and wait:
   - **A — Open a pull request:** push and create the PR only after explicit authorization.
   - **B — Merge locally:** merge into the base only after authorization, then rerun the checks on the merged result.
   - **C — Keep the branch:** leave it as is for later.
   - **D — Discard:** delete the branch or work only after the user explicitly confirms the exact branch and commits to be discarded.

Never choose an option on the user's behalf.

## 10. Rollback and recovery

When changes need to be reverted:

1. Identify the exact commit or operation to undo.
2. Inspect whether the changes have been shared with others.
3. Prefer `git revert` for undoing published commits when appropriate.
4. Consider `git restore` only for explicitly identified paths and only after confirming that the user's work will not be lost.
5. Explain the effects of reset, clean, force-push, and history rewriting before considering them.
6. Create a recovery plan before any potentially destructive operation.

Commands such as `git reset --hard`, `git clean -fd`, `git push --force`, and deletion of branches are prohibited without explicit approval for the specific operation.

Never assume a destructive command is safe merely because it is common in Git workflows.

## 11. WeChat Clone-specific checks

When Git changes involve project refactoring, review the impact on:

- Frontend package manifests, lockfiles, build configuration, and environment-variable conventions.
- Backend Maven configuration, Java source layout, resources, migrations, and tests.
- Dockerfiles, Docker build contexts, Compose files, and service dependencies.
- Gateway/Nginx routing, upstream configuration, and WebSocket proxying.
- Redis Pub/Sub and cross-instance realtime behavior.
- PostgreSQL schema migrations and persistent volumes.
- Object-storage configuration and file-access behavior.
- CI workflows, scripts, healthchecks, and deployment documentation.

Do not move files simply to improve visual organization. Confirm that build paths, imports, scripts, Docker contexts, and CI references remain valid.

Do not commit generated build output, local caches, runtime data, credentials, or temporary debugging artifacts unless the repository explicitly requires them.

## 12. Authorization boundaries

Treat inspection, proposal, and execution as separate steps.

- Read-only inspection: proceed when relevant to the task.
- Reversible local changes: perform only within the user's requested scope.
- Commits, branch switches, fetches, pushes, merges, rebases, and remote operations: follow the user's authorization and applicable repository policies.
- Destructive or history-rewriting operations: obtain explicit approval for the specific action.
- Never change sandbox settings, approval policies, Git configuration, credentials, hooks, or remote configuration merely to bypass a restriction.

If authorization is unclear, present the intended command and its consequences, then wait for approval.

## 13. Final report

After completing a Git task, report:

1. Current branch and relevant commit.
2. Operations performed.
3. Files or paths affected.
4. Tests or checks actually run and their results.
5. Remaining risks or conflicts.
6. Any action that still requires approval.

Distinguish verified results from assumptions. Never claim success when a command failed or its result could not be confirmed.
