# PostgreSQL query diagnostics

Read when investigating a slow or suspicious JDBC query. Run diagnostics only on an isolated or explicitly approved database, and never paste real user data or bind values into reports.

## Measure first

- Get the actual SQL from the repository class and its realistic parameters: the same `limit`, a cursor at mid-history, and a large conversation.
- Run `EXPLAIN (ANALYZE, BUFFERS)` on a representative dataset. A plan from an empty test database says nothing about production.
  - For writes, wrap the statement in `BEGIN; ... ROLLBACK;`, because `ANALYZE` executes it.
- When it is installed, `pg_stat_statements` ordered by `total_exec_time` or `mean_exec_time` shows which queries actually matter.
- Unused indexes: check `pg_stat_user_indexes` where `idx_scan = 0`, taken over a long enough window. Heavy sequential scans: check `seq_scan` against `idx_scan` in `pg_stat_user_tables`.

## Common causes

- **N+1 access:** a repository call inside a loop over conversations or members. Batch it with `IN`/`ANY(?)` or a join.
- **Unbounded result:** no `LIMIT`, or a server-side maximum that is missing.
- **Offset pagination on large history:** use keyset (`created_at`, `id`) instead, matching the V17 index.
- **Index mismatch:** a function or cast on an indexed column in `WHERE`, a mismatched sort direction, or a leading column absent from the predicate.
- **Search:** trigram (`pg_trgm`) indexes support `ILIKE '%term%'`. Check that the query shape still uses them (V8/V18).
- **`SELECT *`:** fetching wide rows or large text when the response needs a few columns.
- **`EXISTS` vs `IN`:** prefer `EXISTS` for membership checks against large sets.
- **Pool saturation:** slow queries holding HikariCP connections look like application latency. Check pool wait time before blaming the query.

## Change rules

- Preserve filters, the authorization predicate, soft-delete handling, ordering, and the pagination contract exactly. A faster query that returns different rows is a defect.
- An index change goes through `database-migration`.
- Report before and after plans and timings measured on comparable data. Without them, label the improvement unmeasured.
