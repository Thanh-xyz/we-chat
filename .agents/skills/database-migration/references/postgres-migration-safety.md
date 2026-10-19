# PostgreSQL migration safety

Read when writing or reviewing a Flyway migration under `wechatbackend/src/main/resources/db/migration`. Verify the Flyway and PostgreSQL versions in use before relying on version-specific behavior.

## Rules

- **Never edit an applied migration.** Add the next `V<n>__description.sql` instead. Check the highest version on the current branch and on `main` to avoid collisions.
- **Expand → migrate → contract** for anything a running older backend still reads:
  1. Add the new nullable column or table.
  2. Deploy code that writes both and reads the new one.
  3. Backfill.
  4. Add constraints.
  5. Drop the old column in a later release.

  During a rolling deploy, replicas on old and new code run at the same time.
- **New columns:**
  - On PostgreSQL 11+, `ADD COLUMN ... DEFAULT <constant>` does not rewrite the table. A volatile default (such as `now()` per row or `gen_random_uuid()`) does.
  - Adding `NOT NULL` to an existing column scans the table. For large tables, add a `CHECK (col IS NOT NULL) NOT VALID` constraint, `VALIDATE` it, and then set `NOT NULL`.
- **Constraints on existing data:** add a foreign key or check as `NOT VALID`, then `VALIDATE CONSTRAINT` separately, to avoid a long exclusive lock. First check for violating rows with a read-only query.
- **Indexes:**
  - `CREATE INDEX` blocks writes on the table while it builds.
  - `CREATE INDEX CONCURRENTLY` does not block writes, but cannot run inside a transaction. Flyway runs such statements non-transactionally, so keep each one in its own migration with no other statements, and confirm Flyway's mixed-transaction setting for the version in use.
  - Existing migrations such as V17 use plain `create index if not exists`. Choose deliberately based on table size and the deploy window, and record why.
- **Index design:**
  - Index foreign keys that are used in joins or deletes; PostgreSQL does not index them automatically.
  - Match composite index order to the `WHERE` and `ORDER BY` of real repository queries, as in `(conversation_id, created_at desc, id desc)`.
  - Use partial indexes for soft-deleted rows (`where deleted_at is null`).
  - Remove redundant indexes only with evidence that they are unused.
- **Lock safety:** for DDL on hot tables (`messages`, `conversation_members`), consider `SET lock_timeout` so a blocked migration fails fast instead of queueing every query behind it.
- **Data backfills:** keep bounded, idempotent backfills in Flyway. Long-running or cross-system movement belongs to `data-pipeline-engineering`. Never delete user data without explicit authorization.
- **Types:** use `timestamptz` for instants, `jsonb` over `json`, `CHECK` or enum constraints for status columns, and explicit `ON DELETE` behavior on every foreign key.

## Verification

- An H2 run does not validate PostgreSQL DDL. Use `cd wechatbackend && ./mvnw verify -Ppostgres-integration`, which needs Docker; mark it `BLOCKED` if Docker is unavailable.
- Test against a database that already contains rows when the migration alters existing data.
- Record the recovery path: a reverse migration, a forward fix, or a restore from backup. Flyway Community has no automatic undo.
