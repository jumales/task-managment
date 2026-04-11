---
name: flyway-migration
description: Create a new Flyway migration file for this project. Guides naming, backward-compatible SQL, and project conventions (nullable columns, partial unique indexes for soft-delete tables, service ownership). Runs the migration-reviewer agent automatically before finishing.
---

You are helping create a Flyway migration for a Spring Boot 3 microservices project using PostgreSQL with soft-deletes (`deleted_at` column).

## What to do

1. **Determine the next version number**
   - Scan the target service's `src/main/resources/db/migration/` directory
   - Find the highest existing `V{n}__` number and add 1
   - If the directory is empty, start at `V1__`

2. **Confirm the service** — migrations live in the service that owns the table(s) being changed. Never put a migration in the wrong service.

3. **Generate the filename** following the pattern: `V{n}__{short_description}.sql`
   - `short_description` uses lowercase with underscores, e.g. `add_due_date_to_tasks`
   - No camelCase, no spaces, no special characters

4. **Write the SQL** applying these rules:
   - New columns must be `NULL` or have a `DEFAULT` (never bare `NOT NULL` on an existing table)
   - Dropping or renaming a column requires a multi-step plan — do NOT do it in one migration; ask the user to confirm the deprecation plan
   - Unique constraints on tables with `deleted_at` must be partial indexes:
     ```sql
     -- correct
     CREATE UNIQUE INDEX uq_tasks_code_project
       ON tasks (task_code, project_id)
       WHERE deleted_at IS NULL;

     -- wrong
     ALTER TABLE tasks ADD CONSTRAINT uq_tasks_code_project UNIQUE (task_code, project_id);
     ```
   - Use `IF NOT EXISTS` / `IF EXISTS` guards on DDL where applicable

5. **Write the file** to `<service>/src/main/resources/db/migration/<filename>.sql`

6. **Run the migration-reviewer agent** on the newly created file and surface any violations to the user before considering the task done.

## Output

After creating the file, show:
- Full file path created
- SQL content
- migration-reviewer result (APPROVED or list of violations)
