---
name: copy
description: "Copy a selected completed-task report from the current Codex context to the system clipboard using an available verified mechanism. Use only when the user explicitly requests copying task output. Do not copy arbitrary project files or automatically continue to another Skill."
---

# Copy Task Output

## Purpose

Copy the user's selected completed-task report to the system clipboard so it can be saved, shared, or reused in a later workflow step.

This Skill is a transport utility. It must preserve the selected content exactly and must not rewrite, summarize, reconstruct, or improve it.

## Immediate execution rule

When the user explicitly invokes this Skill, treat the invocation as authorization for the normal clipboard operation. Select the intended content, detect the available backend, copy it, verify it when possible, and report the result in the same turn.

Do not ask for confirmation, present A/B/C/D choices, or wait for approval before the normal copy operation. Ask a question only when multiple plausible targets exist and choosing incorrectly would be materially misleading or harmful.

## Scope

Use this Skill only when the user explicitly asks to copy task output, such as:

- the latest completed-task report;
- the Scout A–H report;
- the BA analysis report;
- a named section of a report;
- a report including its Markdown headings, code blocks, file paths, or A/B/C/D handoff choices.

Do not use it to copy:

- arbitrary project files;
- `.env` files or secrets;
- source code unless the user explicitly identifies it as the intended task output;
- unrelated terminal history;
- hidden conversation content that is not available in the current context.

## Preconditions

1. Confirm the repository context if the request refers to a project task.
2. Preserve existing project files and Git changes.
3. Identify the exact content to copy from the current context.
4. Detect the operating system, display session, clipboard utilities, and desktop-session reachability before attempting a write.
5. Do not assume that Codex can retrieve arbitrary prior conversation messages.
6. Do not install packages, use `sudo`, or request elevated privileges automatically. The explicit Skill invocation authorizes the normal copy operation only, not system changes or privilege escalation.

The writer, reader, and display probe must run in the same execution context. A utility or display that is available only outside a sandbox, container, remote session, or editor terminal is not usable by default.

## Selecting the content

Use this order:

1. The exact section or report named by the user.
2. The most recent assistant response that is clearly a completed-task report.
3. A single clearly identifiable report in the current context.

If multiple reports or sections are plausible, ask which one to copy only when choosing incorrectly would be materially misleading or harmful. Otherwise choose the most recent relevant assistant-generated content and continue immediately.

If the intended report is not accessible in the current context, do not reconstruct it from memory, summaries, or historical prompts. Report `BLOCKED` and give the smallest copy-friendly fallback. Avoid clarification when a grounded recent response is available.

The selected content must preserve:

- Markdown headings;
- paragraphs;
- numbered and bulleted lists;
- tables;
- code fences;
- file paths;
- status labels;
- A/B/C/D handoff choices;
- whitespace and ordering where practical.

Do not add a wrapper such as `Copied report:` to the clipboard content.

## Sensitive-content check

Before copying, inspect the selected content for obvious sensitive values.

Potential sensitive values include:

- passwords;
- API keys;
- bearer tokens;
- JWTs;
- refresh tokens;
- private storage keys;
- presigned URLs;
- secret environment values;
- private credentials.

If likely sensitive content is present:

1. Do not print the value.
2. Do not copy it automatically.
3. Report `BLOCKED` and state that the selected content appears to contain sensitive material.
4. Request a redacted version or a safer explicitly identified section if needed; this is a safety block, not a normal confirmation step.
5. Do not claim success.

Normal WeChat reports should already exclude secrets according to `AGENTS.md`.

## Clipboard mechanism selection

Detect the active session before selecting a backend. Environment variables alone are not proof of access.

### Wayland

Use `wl-copy` only when:

- `XDG_SESSION_TYPE=wayland` or a valid `WAYLAND_DISPLAY` is present;
- `wl-copy` and, preferably, `wl-paste` are available;
- a read-only Wayland probe or a write/read smoke test succeeds from the same execution context.

The Ubuntu package providing these commands is normally `wl-clipboard`.

### X11

Use `xclip -selection clipboard` or `xsel --clipboard --input` only when:

- `DISPLAY` is present;
- the selected writer is available;
- an X11 probe such as `xprop -root` or `xdpyinfo` succeeds from the same execution context;
- a matching read utility is available when exact read-back verification is required.

The Ubuntu packages are normally `xclip` or `xsel`.

### Other environments

Use `pbcopy`/`pbpaste` on macOS and `clip.exe` or a verified PowerShell reader/writer on Windows. Do not infer host clipboard access from a remote SSH session, container, editor terminal, `tmux`, `xclipboard`, `xprop`, arbitrary D-Bus calls, or terminal escape sequences.

Pass the content through process standard input. Do not place the report in command-line arguments or shell interpolation. Do not print the content while feeding the writer.

Do not use a temporary file unless:

- the available clipboard mechanism requires one;
- no safe stdin mechanism exists;
- the user explicitly approves creating the temporary file;
- the file is created in a narrow temporary directory and removed afterward.

Do not treat a terminal with `DISPLAY` or `WAYLAND_DISPLAY` set as proof that clipboard writing is available.

If the default Codex execution context cannot reach the desktop but an elevated or host-side probe can, classify the default path as `BLOCKED`. Ask the user before using an explicitly approved host-side or elevated operation for the actual copy. Do not silently switch execution contexts.

## Missing dependency or desktop bridge

If the active desktop session is reachable but no suitable writer exists:

1. Report the missing command and detected session, without printing environment secrets.
2. Identify the smallest setup, normally `xclip`/`xsel` for X11 or `wl-clipboard` for Wayland.
3. Do not install packages or change system configuration automatically.
4. Report the smallest practical setup or manual fallback. Only perform setup in a later task that explicitly requests installation or system configuration.

If the desktop session is not reachable, explain that installing a utility alone may not solve the problem. Identify the boundary: sandbox, container, SSH, remote editor, or missing display authorization. Offer a host-side bridge or manual copy fallback only when it is actually available and authorized.

Never use `sudo` or privileged system changes automatically.

## Confirming success

Never claim that copying succeeded unless the clipboard operation produced direct success evidence.

Use this validation order:

1. Check that the writer process exits successfully or reports a verified asynchronous handoff.
2. When a matching reader is available, read the clipboard back without printing its contents.
3. Compare the read-back content exactly, including Unicode, newlines, Markdown, and code fences, or compare a local length/hash without exposing the report.
4. If the writer is asynchronous and cannot be verified, report `PARTIAL`, `UNCERTAIN`, or `BLOCKED`, not `PASS`.

Examples of matching readers:

- `pbpaste` for `pbcopy`;
- `wl-paste` for `wl-copy`;
- `xclip -selection clipboard -o` for `xclip`;
- `xsel --clipboard --output` for `xsel`;
- PowerShell clipboard retrieval for `clip.exe`.

Do not overwrite the clipboard with a second attempt unless the user asks to retry or selects another mechanism.

## Clipboard smoke test

When a backend is newly configured or its desktop reachability is uncertain, run one harmless smoke test before copying a real report only when the check is necessary. A normal copy does not require a second smoke test when the backend and read-back path were already established.

1. Use a unique, non-sensitive test string containing representative Unicode and Markdown characters.
2. If a matching reader can capture the existing clipboard and restore it reliably in memory, preserve and restore the existing content without printing it.
3. If the existing clipboard cannot be safely preserved and restored, do not overwrite it for a smoke test. Report `BLOCKED` and provide a manual fallback; do not ask for confirmation merely to run the smoke test.
4. Write the test string through standard input.
5. Read it back through the same desktop session and compare it exactly.
6. Restore the previous clipboard when it was safely preserved and verify restoration when possible.

Classify the smoke test as:

- `PASS`: write and exact read-back succeeded;
- `PARTIAL`: write returned success but exact read-back was unavailable;
- `BLOCKED`: no usable connection to the user's clipboard exists;
- `FAIL`: write or verification failed.

Do not use a real task report, project secret, token, object key, or presigned URL as a smoke-test value.

## No mechanism available

If no supported writer is available:

- report `BLOCKED`;
- identify whether the missing capability is a writer utility, read-back utility, display connection, or host bridge;
- identify the appropriate Ubuntu package or setup;
- do not install packages, change configuration, or request elevated access automatically;
- provide the exact selected content in a copy-friendly fenced code block;
- explain that the user can copy it manually.

Do not describe `tmux`, `xclipboard`, a terminal escape sequence, or a host-side diagnostic as successful system clipboard access unless the actual selected content was written and verified in the user's clipboard context.

## Completion report

After the attempt, report concisely:

- selected content: identify it by report title or section, not by reproducing sensitive content;
- backend selected or `none`;
- status: `PASS`, `PARTIAL`, `BLOCKED`, `FAIL`, or `CANCELLED`;
- smoke-test result, if run;
- verification performed;
- missing dependency, session boundary, or host-bridge limitation;
- limitation or fallback.

Return this result immediately. Do not automatically invoke another Skill or begin the next workflow step.

## No confirmation handoff

Do not present choices or wait for the user's approval after the copy attempt. If the result is `BLOCKED`, `FAIL`, or `PARTIAL`, state the concrete limitation and the smallest practical workaround in the completion report.
