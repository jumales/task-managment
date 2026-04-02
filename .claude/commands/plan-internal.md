# Plan Task

Guide the user through planning a new task before any code is written.

## Workflow

### Step 1 — Understand the topic

If the user provided a task description as the argument to `/plan`, use it.
If not, ask: **"What task would you like to plan?"**

### Step 2 — Ask whether a plan file is needed

Ask the user: **"Should I create a plan file for this? (yes/no)"**

- If **no**: proceed directly without writing a file. Give a brief verbal overview of the approach and stop.
- If **yes**: continue to Step 3.

### Step 3 — Create the plan file

1. Create the `plans/` directory at the project root if it does not exist.
2. Choose a filename: `plans/<topic-slug>.md` using kebab-case (e.g. `plans/add-user-roles.md`).
3. Write the plan file using the structure below.

### Step 4 — Present and confirm

After writing the file, display its contents and ask:
**"Does this plan look right? Say 'start' to begin Chunk 1, or tell me what to adjust."**

---

## Execution rules (apply when the user says 'start' or 'continue')

- **Create a branch first** — run `git checkout -b <branch_name>` before writing any code
- **Open a PR after the first chunk** — as soon as the first chunk is committed and pushed, create a PR so the user can review progress; subsequent chunks are pushed to the same branch/PR
- **Never proceed to the next chunk automatically** — after each chunk is committed, stop and wait for the user to say 'continue' (or similar); this gives the user control over pacing and review

---

## Plan file structure

```markdown
# <Task Title>

**Goal**: One sentence — what this task delivers when complete.

**Branch**: `<snake_case_branch_name>` (follows CLAUDE.md naming)

---

## Chunks

Each chunk is one atomic unit of work and one git commit.

### Chunk 1 — <short label>
**What**: Describe what to implement.
**Files**: List the files to create or modify.
**Commit**: `<imperative commit message>`

### Chunk 2 — <short label>
**What**: ...
**Files**: ...
**Commit**: ...

<!-- Add as many chunks as needed -->
```

---

## Chunk sizing rules

- **One chunk = one commit** — scope each chunk so it can be committed independently.
- **Split by layer**, not by feature. Typical split for a backend feature:
  - Chunk 1: Flyway migration
  - Chunk 2: Entity + Repository
  - Chunk 3: Service
  - Chunk 4: Controller + OpenAPI
  - Chunk 5: Integration tests
  - Chunk 6: Frontend (types, API client, UI component)
- **Keep chunks focused** — never bundle schema changes with controller logic in one chunk.
- **Each chunk must pass the build** — the codebase should compile and existing tests should pass after every chunk.

## Reminders (from CLAUDE.md)

- Create a branch before starting: `git checkout -b <branch_name>`
- One PR per task — open it after the **first** chunk is committed (not the last)
- Run `mvn clean install -DskipTests=true` before pushing
- Check CI with `gh run watch` after pushing
