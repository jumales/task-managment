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

### Plans with 2 or fewer chunks (simple flow)

- **Create a branch first** — run `git checkout -b <branch_name>` before writing any code
- **Commit the plan file in the first chunk** — include `plans/<topic-slug>.md` in the first chunk's commit
- **Open a PR after the first chunk** — push and create a PR to `main`; subsequent chunks push to the same branch/PR
- **Never proceed to the next chunk automatically** — stop after each commit and wait for the user to say 'continue'

### Plans with more than 2 chunks (feature-branch flow)

When the plan has 3 or more chunks, use isolated chunk branches so each piece of work can be reviewed and approved before the next begins.

**Setup (before Chunk 1):**
1. Create a long-lived feature branch from `main`: `git checkout -b <feature_branch_name>` (same snake_case name as the plan's **Branch** field)
2. Push the feature branch immediately: `git push -u origin <feature_branch_name>`

**For each chunk:**
1. From the feature branch, create a chunk branch: `git checkout -b <feature_branch_name>_chunk_<N>` (e.g., `add_keycloak_role_management_ui_chunk_1`)
2. Implement the chunk
3. Run `mvn clean install -DskipTests=true` and confirm it passes
4. Commit (include `plans/<topic-slug>.md` in Chunk 1's commit)
5. Push the chunk branch and open a PR **targeting the feature branch** (not `main`):
   ```
   gh pr create --base <feature_branch_name> --title "Chunk N — <label>" --body "..."
   ```
6. **Stop and wait** — do not proceed to the next chunk until the user explicitly approves (says 'continue', 'approved', 'merge', or similar)
7. Once approved, merge the chunk PR into the feature branch, then delete the chunk branch

**After all chunks are approved and merged:**
- Open a final PR from the feature branch to `main` summarising the full set of changes

**Why this flow?**
Each chunk PR is small and focused, making review fast. Pausing between chunks lets the user catch issues early before they compound across later chunks.

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

- **≤ 2 chunks**: one branch, PR to `main` after Chunk 1
- **> 2 chunks**: feature branch first, then one chunk branch + chunk PR (targeting feature branch) per chunk; wait for approval before proceeding; final PR from feature branch to `main`
- Run `mvn clean install -DskipTests=true` before every push
- Check CI with `gh run watch` after pushing
