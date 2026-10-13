# Codex Integration

Converts all Agency agents into Codex custom agent TOML files. Each source
agent becomes one standalone `.toml` file containing the minimal Codex-required
fields: `name`, `description`, and `developer_instructions`.

## Installation

### Prerequisites

- [Codex](https://developers.openai.com/codex/overview) installed

### Convert And Install

```bash
# Generate integration files (required on fresh clone)
./scripts/convert.sh --tool codex

# Install agents
./scripts/install.sh --tool codex
```

This copies generated agent files to `~/.codex/agents/`.

## Generated Format

Each generated file lives in:

```text
integrations/codex/agents/<slug>.toml
```

The mapping is intentionally minimal:

- `name` is copied from the source frontmatter unchanged
- `description` is copied from the source frontmatter unchanged
- `developer_instructions` contains the full Markdown body unchanged

Source-only metadata such as `color`, `emoji`, `vibe`, and other unsupported
frontmatter fields are omitted.

## Usage

After installation, reference the custom agent by name in Codex:

```text
Use the Frontend Developer agent to review this component.
```

Codex uses the exact `name` field inside the TOML file as the agent identifier.
The generated filename slug is only for filesystem safety; it is not an alias.
For example, `agents/backend-architect.toml` declares `name = "Backend Architect"`,
so a direct multi-agent request must use:

```text
Use the spawn_agent tool with agent_type='Backend Architect'.
```

`backend_architect` and `backend-architect` are different identifiers and will
not resolve to that file. Codex discovers the files in `~/.codex/agents/`
automatically; no `[agents.<name>]` entry is needed for the generated agents.

If you use an explicit `[agents.<name>]` declaration with `config_file`, keep
the role's `name` in that TOML file identical to the identifier you spawn.
`config_file` loads a role configuration layer; it does not create an alias for
a different `name`.

## Regenerate

After modifying source agents:

```bash
./scripts/convert.sh --tool codex
./scripts/install.sh --tool codex
```

## Troubleshooting

### Codex integration not found

Generate the Codex artifacts before installing:

```bash
./scripts/convert.sh --tool codex
```

### Codex not detected

Make sure `codex` is in your PATH, or that `~/.codex/` already exists:

```bash
which codex
codex --help
```
