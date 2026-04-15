#!/usr/bin/env python3
# pip install psycopg2-binary faker

"""
Seed script: inserts 10,000 tasks (and all related data) directly into PostgreSQL.
Bypasses the API and Kafka — use this for performance/load testing only.

Usage:
    python scripts/seed_task_data.py                    # localhost:5432
    python scripts/seed_task_data.py --dry-run          # prints counts, no writes
    python scripts/seed_task_data.py --host H --port P  # custom DB host/port
"""

# ─────────────────────────────────────────────────────────────────────────────
# Section A — Imports
# ─────────────────────────────────────────────────────────────────────────────
import argparse
import random
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone

import psycopg2
import psycopg2.extras
from faker import Faker

# ─────────────────────────────────────────────────────────────────────────────
# Section B — Configuration
# ─────────────────────────────────────────────────────────────────────────────

DB_CONFIGS = {
    "task_db": {
        "dbname": "task_db",
        "user": "task_svc",
        "password": "task_svc_pass",
    },
    "file_db": {
        "dbname": "file_db",
        "user": "file_svc",
        "password": "file_svc_pass",
    },
    "reporting_db": {
        "dbname": "reporting_db",
        "user": "reporting_svc",
        "password": "reporting_svc_pass",
    },
    "audit_db": {
        "dbname": "audit_db",
        "user": "audit_svc",
        "password": "audit_svc_pass",
    },
}

# Keycloak seed user IDs — hardcoded in demo-realm.json
USER_IDS = [
    "11111111-1111-1111-1111-111111111101",  # admin-user
    "11111111-1111-1111-1111-111111111102",  # dev-user
    "11111111-1111-1111-1111-111111111103",  # qa-user
    "11111111-1111-1111-1111-111111111104",  # devops-user
    "11111111-1111-1111-1111-111111111105",  # pm-user
    "11111111-1111-1111-1111-111111111106",  # supervisor-user
]

USER_NAMES = {
    "11111111-1111-1111-1111-111111111101": "Admin User",
    "11111111-1111-1111-1111-111111111102": "Dev User",
    "11111111-1111-1111-1111-111111111103": "QA User",
    "11111111-1111-1111-1111-111111111104": "Devops User",
    "11111111-1111-1111-1111-111111111105": "PM User",
    "11111111-1111-1111-1111-111111111106": "Supervisor User",
}

PROJECT_NAMES = [
    ("Seed Project Alpha",   "Backend platform overhaul and API modernisation"),
    ("Seed Project Beta",    "Mobile application for field operations"),
    ("Seed Project Gamma",   "Data analytics and reporting dashboard"),
    ("Seed Project Delta",   "Customer-facing portal and self-service tools"),
    ("Seed Project Epsilon", "Infrastructure automation and DevOps tooling"),
]

# Per-project user weights (index matches USER_IDS order: admin, dev, qa, devops, pm, supervisor).
# Different rates make the "My Open Tasks vs Total" chart visually interesting.
PROJECT_USER_WEIGHTS = {
    "Seed Project Alpha":   [5, 1, 1, 1, 1, 1],  # admin ~50 %
    "Seed Project Beta":    [1, 4, 3, 2, 2, 2],  # admin ~7 %
    "Seed Project Gamma":   [2, 2, 2, 2, 2, 2],  # even ~17 %
    "Seed Project Delta":   [3, 1, 1, 2, 1, 1],  # admin ~33 %
    "Seed Project Epsilon": [1, 1, 1, 1, 3, 2],  # pm-heavy, admin ~11 %
}

PHASE_NAMES = [
    "PLANNING", "BACKLOG", "TODO", "IN_PROGRESS",
    "IN_REVIEW", "TESTING", "DONE", "RELEASED", "REJECTED",
]

TASK_STATUSES = ["TODO", "IN_PROGRESS", "DONE"]

TASK_TYPES = [
    "FEATURE", "BUG_FIXING", "TESTING", "PLANNING",
    "TECHNICAL_DEBT", "DOCUMENTATION", "OTHER",
]

WORK_TYPES = [
    "DEVELOPMENT", "TESTING", "CODE_REVIEW", "DESIGN",
    "PLANNING", "DOCUMENTATION", "DEPLOYMENT", "MEETING", "OTHER",
]

PARTICIPANT_ROLES = ["CREATOR", "ASSIGNEE", "CONTRIBUTOR", "WATCHER"]

TIMELINE_STATES = [
    "PLANNED_START", "PLANNED_END", "REAL_START", "REAL_END", "RELEASE_DATE",
]

ATTACHMENT_CONTENT_TYPES = [
    ("application/pdf",         "document.pdf"),
    ("image/png",               "screenshot.png"),
    ("image/jpeg",              "photo.jpg"),
    ("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "report.docx"),
    ("text/plain",              "notes.txt"),
]

# Phase distribution: must sum to 1.0
PHASE_WEIGHTS = {
    "RELEASED":    0.60,
    "DONE":        0.10,
    "REJECTED":    0.10,
    # Remaining 20% spread evenly across 6 active phases
    "PLANNING":    0.20 / 6,
    "BACKLOG":     0.20 / 6,
    "TODO":        0.20 / 6,
    "IN_PROGRESS": 0.20 / 6,
    "IN_REVIEW":   0.20 / 6,
    "TESTING":     0.20 / 6,
}

# Phases where task fields are locked (Java domain: TaskPhaseName.FIELD_LOCKED_PHASES)
FIELD_LOCKED_PHASES = {"DONE", "RELEASED", "REJECTED"}

# Phases where all writes are blocked (Java domain: TaskPhaseName.FINISHED_PHASES)
FINISHED_PHASES = {"RELEASED", "REJECTED"}

TASKS_PER_PROJECT = 2000
NUM_PROJECTS = 5
BATCH_SIZE = 500

# ─────────────────────────────────────────────────────────────────────────────
# Section C — Connection and batch-insert helpers
# ─────────────────────────────────────────────────────────────────────────────

def get_conn(db_name: str, host: str, port: int):
    """Open a psycopg2 connection for the named logical database."""
    cfg = dict(DB_CONFIGS[db_name])
    cfg["host"] = host
    cfg["port"] = port
    return psycopg2.connect(**cfg)


def batch_insert(conn, sql: str, rows: list, dry_run: bool) -> None:
    """
    Insert rows using execute_values for efficiency.
    In dry-run mode, prints the row count without touching the DB.
    """
    if not rows:
        return
    if dry_run:
        print(f"  [dry-run] {len(rows):,} rows → {sql[:70].strip()}...")
        return
    with conn.cursor() as cur:
        psycopg2.extras.execute_values(cur, sql, rows, page_size=BATCH_SIZE)
    conn.commit()


def now_utc() -> datetime:
    """Return timezone-aware UTC now."""
    return datetime.now(timezone.utc)


def random_past_dt(max_days_ago: int = 730) -> datetime:
    """Return a random UTC datetime within the last max_days_ago days."""
    return now_utc() - timedelta(
        days=random.randint(0, max_days_ago),
        hours=random.randint(0, 23),
        minutes=random.randint(0, 59),
    )


def uid() -> str:
    """Return a new UUID4 string."""
    return str(uuid.uuid4())


# ─────────────────────────────────────────────────────────────────────────────
# Section D — Pure data generators (no DB calls)
# ─────────────────────────────────────────────────────────────────────────────

def make_project(index: int) -> dict:
    """Build a task_projects row dict for the given project index (0-based)."""
    name, description = PROJECT_NAMES[index]
    return {
        "id": uid(),
        "name": name,
        "description": description,
        "task_code_prefix": "TASK_",
        "next_task_number": 1,
        "default_phase_id": None,  # set after phases are created
        "deleted_at": None,
    }


def make_phases(project_id: str) -> list[dict]:
    """Build all 9 task_phases rows for a project."""
    return [
        {
            "id": uid(),
            "project_id": project_id,
            "name": phase_name,
            "description": f"{phase_name.replace('_', ' ').title()} phase",
            "custom_name": None,
            "deleted_at": None,
        }
        for phase_name in PHASE_NAMES
    ]


def pick_phase_name() -> str:
    """Weighted random selection of a phase name according to PHASE_WEIGHTS."""
    phases = list(PHASE_WEIGHTS.keys())
    weights = [PHASE_WEIGHTS[p] for p in phases]
    return random.choices(phases, weights=weights, k=1)[0]


def status_for_phase(phase_name: str) -> str:
    """
    Choose a TaskStatus that is semantically appropriate for the given phase.
    FIELD_LOCKED_PHASES always get DONE. Active phases get a weighted mix.
    """
    if phase_name in FIELD_LOCKED_PHASES:
        return "DONE"
    if phase_name in ("PLANNING", "BACKLOG", "TODO"):
        return random.choices(["TODO", "IN_PROGRESS"], weights=[0.8, 0.2], k=1)[0]
    # IN_PROGRESS, IN_REVIEW, TESTING
    return random.choices(["TODO", "IN_PROGRESS", "DONE"], weights=[0.1, 0.6, 0.3], k=1)[0]


def progress_for_status(status: str) -> int:
    """Derive a sensible progress percentage from a task status."""
    if status == "DONE":
        return 100
    if status == "IN_PROGRESS":
        return random.randint(10, 90)
    return 0


def make_task(task_number: int, project_id: str, phase_id: str,
              phase_name: str, fake: Faker, project_name: str = "") -> dict:
    """Build a tasks row dict. Uses per-project user weights when project_name is supplied."""
    weights = PROJECT_USER_WEIGHTS.get(project_name)
    assigned_user_id = (
        random.choices(USER_IDS, weights=weights, k=1)[0]
        if weights
        else random.choice(USER_IDS)
    )
    status = status_for_phase(phase_name)
    return {
        "id": uid(),
        "title": fake.sentence(nb_words=random.randint(4, 10)).rstrip("."),
        "description": fake.paragraph(nb_sentences=random.randint(1, 5)),
        "status": status,
        "type": random.choice(TASK_TYPES),
        "progress": progress_for_status(status),
        "task_code": f"TASK_{task_number}",
        "assigned_user_id": assigned_user_id,
        "project_id": project_id,
        "phase_id": phase_id,
        "deleted_at": None,
        # stored for downstream generators; not a real column
        "_creator_user_id": random.choice(USER_IDS),
        "_phase_name": phase_name,
        "_assigned_user_id": assigned_user_id,
    }


def make_comments(task_id: str, n: int, fake: Faker) -> list[tuple]:
    """Build n task_comments rows as tuples for execute_values."""
    return [
        (
            uid(),
            task_id,
            random.choice(USER_IDS),
            fake.text(max_nb_chars=500),
            random_past_dt(),
            None,  # deleted_at
        )
        for _ in range(n)
    ]


def make_participants(task_id: str, creator_id: str,
                      assignee_id: str, n: int) -> list[tuple]:
    """
    Build up to n task_participants rows.
    Always includes CREATOR; adds ASSIGNEE if different user.
    Fills remaining slots with CONTRIBUTOR/WATCHER combos, de-duplicated via a set.
    """
    seen: set[tuple[str, str]] = set()
    rows: list[tuple] = []
    created_at = random_past_dt()

    def add(user_id: str, role: str) -> None:
        key = (user_id, role)
        if key not in seen:
            seen.add(key)
            rows.append((uid(), task_id, user_id, role, created_at, None))

    add(creator_id, "CREATOR")
    if assignee_id != creator_id:
        add(assignee_id, "ASSIGNEE")

    extra_roles = ["CONTRIBUTOR", "WATCHER"]
    attempts = 0
    while len(rows) < n and attempts < 50:
        add(random.choice(USER_IDS), random.choice(extra_roles))
        attempts += 1

    return rows


def make_planned_works(task_id: str, n: int) -> list[tuple]:
    """
    Build n task_planned_works rows with distinct work_types.
    Uses random.sample to avoid UNIQUE (task_id, work_type) violations.
    planned_hours: 1–5 per entry.
    """
    created_at = random_past_dt()
    return [
        (uid(), task_id, random.choice(USER_IDS), work_type,
         random.randint(1, 5), created_at)
        for work_type in random.sample(WORK_TYPES, min(n, len(WORK_TYPES)))
    ]


def make_booked_works(task_id: str, n: int) -> list[tuple]:
    """
    Build n task_booked_works rows.
    booked_hours: 1–50 per entry.
    """
    created_at = random_past_dt()
    return [
        (uid(), task_id, random.choice(USER_IDS),
         random.choice(WORK_TYPES), random.randint(1, 50), created_at, None)
        for _ in range(n)
    ]


def make_timelines(task_id: str, phase_name: str,
                   user_id: str, base_date: datetime) -> list[tuple]:
    """
    Build task_timelines rows appropriate for the given phase.
    Dates advance logically: planned_start → planned_end → real_start → real_end → release_date.
    """
    user_name = USER_NAMES[user_id]
    created_at = now_utc()

    planned_start = base_date
    planned_end   = planned_start + timedelta(days=random.randint(3, 30))
    real_start    = planned_start + timedelta(days=random.randint(0, 5))
    real_end      = real_start    + timedelta(days=random.randint(3, 30))
    release_date  = real_end      + timedelta(days=random.randint(0, 7))

    # Determine which states to emit based on phase
    states_to_emit = []
    if phase_name in ("PLANNING", "BACKLOG", "TODO"):
        states_to_emit = ["PLANNED_START", "PLANNED_END"]
    elif phase_name in ("IN_PROGRESS", "IN_REVIEW", "TESTING"):
        states_to_emit = ["PLANNED_START", "PLANNED_END", "REAL_START"]
    elif phase_name in ("DONE", "REJECTED"):
        states_to_emit = ["PLANNED_START", "PLANNED_END", "REAL_START", "REAL_END"]
    elif phase_name == "RELEASED":
        states_to_emit = ["PLANNED_START", "PLANNED_END", "REAL_START", "REAL_END", "RELEASE_DATE"]

    timestamps = {
        "PLANNED_START": planned_start,
        "PLANNED_END":   planned_end,
        "REAL_START":    real_start,
        "REAL_END":      real_end,
        "RELEASE_DATE":  release_date,
    }

    return [
        (uid(), task_id, state, timestamps[state], user_id, user_name, created_at, None)
        for state in states_to_emit
    ]


def make_attachments(task_id: str, n: int, uploader_id: str,
                     fake: Faker) -> tuple[list[tuple], list[tuple]]:
    """
    Build n attachment rows as two parallel lists:
    - file_meta_rows: for file_db.file_metadata
    - attachment_rows: for task_db.task_attachments

    Both lists share the same file_id so the reference is consistent.
    """
    file_meta_rows = []
    attachment_rows = []
    uploaded_at = random_past_dt()

    for _ in range(n):
        file_id = uid()
        content_type, base_name = random.choice(ATTACHMENT_CONTENT_TYPES)
        # Make filename slightly unique
        original_filename = f"{fake.word()}_{base_name}"
        object_key = f"attachments/{file_id}/{original_filename}"

        file_meta_rows.append((
            file_id,
            "attachments",
            object_key,
            content_type,
            original_filename,
            uploader_id,         # stored as string in file_db
            uploaded_at,
            None,                # deleted_at
        ))
        attachment_rows.append((
            uid(),
            task_id,
            file_id,
            original_filename,
            content_type,
            uploader_id,
            uploaded_at,
        ))

    return file_meta_rows, attachment_rows


# ─────────────────────────────────────────────────────────────────────────────
# Section E — Seed orchestrator
# ─────────────────────────────────────────────────────────────────────────────

def seed(args) -> None:
    """Main orchestration function. Connects to all DBs and inserts data in FK-safe order."""
    host, port, dry_run = args.host, args.port, args.dry_run
    fake = Faker()
    Faker.seed(42)
    random.seed(42)

    # ── Open connections ──────────────────────────────────────────────────────
    print("Connecting to databases...")
    conn_task      = get_conn("task_db",      host, port)
    conn_file      = get_conn("file_db",      host, port)
    conn_reporting = get_conn("reporting_db", host, port)
    conn_audit     = get_conn("audit_db",     host, port)

    # ── Idempotency check ─────────────────────────────────────────────────────
    if not dry_run:
        with conn_task.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM task_projects WHERE name LIKE 'Seed Project%'")
            existing = cur.fetchone()[0]
        if existing > 0:
            print(f"Seed data already exists ({existing} seed project(s) found). "
                  "Drop them first if you want to re-seed.")
            sys.exit(0)

    # ── 1. Build projects + phases in memory ─────────────────────────────────
    projects = [make_project(i) for i in range(NUM_PROJECTS)]
    # phase_index[project_id][phase_name] = phase_id
    phase_index: dict[str, dict[str, str]] = {}
    all_phase_rows = []

    for project in projects:
        phases = make_phases(project["id"])
        phase_index[project["id"]] = {p["name"]: p["id"] for p in phases}
        # Set default phase to BACKLOG
        project["default_phase_id"] = phase_index[project["id"]]["BACKLOG"]
        all_phase_rows.extend(phases)

    # ── 2. Insert projects ────────────────────────────────────────────────────
    print(f"\nInserting {len(projects)} projects...")
    batch_insert(
        conn_task,
        """INSERT INTO task_projects
           (id, name, description, task_code_prefix, next_task_number, default_phase_id, deleted_at)
           VALUES %s""",
        [(p["id"], p["name"], p["description"], p["task_code_prefix"],
          p["next_task_number"], p["default_phase_id"], p["deleted_at"])
         for p in projects],
        dry_run,
    )

    # ── 3. Insert phases ──────────────────────────────────────────────────────
    print(f"Inserting {len(all_phase_rows)} phases...")
    batch_insert(
        conn_task,
        """INSERT INTO task_phases (id, project_id, name, description, custom_name, deleted_at)
           VALUES %s""",
        [(p["id"], p["project_id"], p["name"], p["description"],
          p["custom_name"], p["deleted_at"])
         for p in all_phase_rows],
        dry_run,
    )

    # ── 4-9. Iterate projects — insert tasks and all child records ────────────
    start_time = time.perf_counter()
    total_tasks       = 0
    total_comments    = 0
    total_participants = 0
    total_planned     = 0
    total_booked      = 0
    total_timelines   = 0
    total_attachments = 0

    for project in projects:
        project_id = project["id"]
        print(f"\n→ Seeding {TASKS_PER_PROJECT:,} tasks for '{project['name']}'...")

        # ── Pass 1: generate and insert all tasks first ───────────────────────
        # Must complete before children are inserted to satisfy FK constraints.
        project_tasks = []  # holds full task dicts for pass 2
        task_rows     = []

        for task_number in range(1, TASKS_PER_PROJECT + 1):
            phase_name  = pick_phase_name()
            phase_id    = phase_index[project_id][phase_name]
            task        = make_task(task_number, project_id, phase_id, phase_name, fake, project["name"])
            task["_base_date"] = random_past_dt(730)
            project_tasks.append(task)
            task_rows.append((
                task["id"], task["title"], task["description"], task["status"],
                task["type"], task["progress"], task["task_code"],
                task["assigned_user_id"], project_id, phase_id, task["deleted_at"],
            ))

        batch_insert(conn_task,
            """INSERT INTO tasks
               (id, title, description, status, type, progress, task_code,
                assigned_user_id, project_id, phase_id, deleted_at)
               VALUES %s""",
            task_rows, dry_run)
        total_tasks += len(project_tasks)
        print(f"  tasks committed ({total_tasks:,} total), generating children...")

        # ── Pass 2: generate children and insert in batches ───────────────────
        # All tasks for this project are now in the DB — FK constraints are safe.
        buf_comments      = []
        buf_participants  = []
        buf_planned       = []
        buf_booked        = []
        buf_timelines     = []
        buf_attachments   = []
        buf_file_meta     = []
        buf_report_tasks  = []
        buf_report_planned = []
        buf_report_booked  = []
        buf_audit_records  = []
        buf_phase_audit    = []

        def flush(buf, conn, sql):
            """Flush the buffer to DB and clear it."""
            if buf:
                batch_insert(conn, sql, buf, dry_run)
                buf.clear()

        def flush_if_needed(buf, conn, sql):
            """Flush buffer when it reaches BATCH_SIZE."""
            if len(buf) >= BATCH_SIZE:
                flush(buf, conn, sql)

        COMMENTS_SQL     = "INSERT INTO task_comments (id, task_id, user_id, content, created_at, deleted_at) VALUES %s"
        PARTICIPANTS_SQL = "INSERT INTO task_participants (id, task_id, user_id, role, created_at, deleted_at) VALUES %s"
        PLANNED_SQL      = "INSERT INTO task_planned_works (id, task_id, user_id, work_type, planned_hours, created_at) VALUES %s"
        BOOKED_SQL       = "INSERT INTO task_booked_works (id, task_id, user_id, work_type, booked_hours, created_at, deleted_at) VALUES %s"
        TIMELINES_SQL    = "INSERT INTO task_timelines (id, task_id, state, timestamp, set_by_user_id, set_by_user_name, created_at, deleted_at) VALUES %s"
        ATTACHMENTS_SQL  = "INSERT INTO task_attachments (id, task_id, file_id, file_name, content_type, uploaded_by_user_id, uploaded_at) VALUES %s"
        FILE_META_SQL    = "INSERT INTO file_metadata (id, bucket, object_key, content_type, original_filename, uploaded_by, uploaded_at, deleted_at) VALUES %s"

        for task in project_tasks:
            task_id     = task["id"]
            phase_name  = task["_phase_name"]
            creator_id  = task["_creator_user_id"]
            assignee_id = task["_assigned_user_id"]
            base_date   = task["_base_date"]

            # ── comments (1–100) ───────────────────────────────────────────────
            num_comments = random.randint(1, 100)
            buf_comments.extend(make_comments(task_id, num_comments, fake))
            total_comments += num_comments

            # ── participants (1–5) ────────────────────────────────────────────
            num_participants = random.randint(1, 5)
            participants = make_participants(task_id, creator_id, assignee_id, num_participants)
            buf_participants.extend(participants)
            total_participants += len(participants)

            # ── planned works (1–5 distinct work types) ───────────────────────
            num_planned = random.randint(1, 5)
            planned = make_planned_works(task_id, num_planned)
            buf_planned.extend(planned)
            total_planned += len(planned)

            # ── booked works (1–3 entries, 1–50 hours each) ───────────────────
            num_booked = random.randint(1, 3)
            booked = make_booked_works(task_id, num_booked)
            buf_booked.extend(booked)
            total_booked += len(booked)

            # ── timelines ─────────────────────────────────────────────────────
            timelines = make_timelines(task_id, phase_name, assignee_id, base_date)
            buf_timelines.extend(timelines)
            total_timelines += len(timelines)

            # ── attachments (0–2) ─────────────────────────────────────────────
            num_attachments = random.randint(0, 2)
            if num_attachments > 0:
                file_meta, attach = make_attachments(task_id, num_attachments, assignee_id, fake)
                buf_file_meta.extend(file_meta)
                buf_attachments.extend(attach)
                total_attachments += num_attachments

            # ── reporting_db: report_tasks ────────────────────────────────────
            planned_start_ts = base_date
            planned_end_ts   = base_date + timedelta(days=random.randint(3, 30))
            buf_report_tasks.append((
                task_id, task["task_code"], task["title"], task["description"],
                task["status"], project_id, project["name"],
                task["phase_id"], phase_name,
                assignee_id, USER_NAMES.get(assignee_id),
                planned_start_ts, planned_end_ts,
                now_utc(), None,
            ))

            # ── reporting_db: report_planned_works ────────────────────────────
            for pw in planned:
                buf_report_planned.append((pw[0], task_id, project_id, pw[2], pw[3], pw[4], now_utc()))

            # ── reporting_db: report_booked_works ─────────────────────────────
            for bw in booked:
                buf_report_booked.append((bw[0], task_id, project_id, bw[2], bw[3], bw[4], now_utc(), None))

            # ── audit_db records ──────────────────────────────────────────────
            buf_audit_records.append((
                uid(), task_id, assignee_id, None, task["status"], base_date, now_utc(),
            ))
            buf_phase_audit.append((
                uid(), task_id, creator_id, None, None, task["phase_id"], phase_name, base_date, now_utc(),
            ))

            # ── Flush child buffers when full ──────────────────────────────────
            flush_if_needed(buf_comments,     conn_task, COMMENTS_SQL)
            flush_if_needed(buf_participants, conn_task, PARTICIPANTS_SQL)
            flush_if_needed(buf_planned,      conn_task, PLANNED_SQL)
            flush_if_needed(buf_booked,       conn_task, BOOKED_SQL)
            flush_if_needed(buf_timelines,    conn_task, TIMELINES_SQL)
            flush_if_needed(buf_attachments,  conn_task, ATTACHMENTS_SQL)
            flush_if_needed(buf_file_meta,    conn_file, FILE_META_SQL)

            # ── Progress report ────────────────────────────────────────────────
            if total_comments % 50000 < 100:  # approximate — print every ~50k comments
                elapsed = time.perf_counter() - start_time
                print(f"  comments={total_comments:,}  elapsed={elapsed:.1f}s")

        # ── End of project: flush all remaining child buffers ─────────────────
        flush(buf_comments,     conn_task, COMMENTS_SQL)
        flush(buf_participants, conn_task, PARTICIPANTS_SQL)
        flush(buf_planned,      conn_task, PLANNED_SQL)
        flush(buf_booked,       conn_task, BOOKED_SQL)
        flush(buf_timelines,    conn_task, TIMELINES_SQL)
        flush(buf_attachments,  conn_task, ATTACHMENTS_SQL)
        flush(buf_file_meta,    conn_file, FILE_META_SQL)

        # ── 10. Update next_task_number on the project ─────────────────────────
        if not dry_run:
            with conn_task.cursor() as cur:
                cur.execute(
                    "UPDATE task_projects SET next_task_number = %s WHERE id = %s",
                    (TASKS_PER_PROJECT + 1, project_id),
                )
            conn_task.commit()

        # ── 11. Insert reporting_db rows ──────────────────────────────────────
        batch_insert(conn_reporting,
            """INSERT INTO report_tasks
               (id, task_code, title, description, status, project_id, project_name,
                phase_id, phase_name, assigned_user_id, assigned_user_name,
                planned_start, planned_end, updated_at, deleted_at)
               VALUES %s""",
            buf_report_tasks, dry_run)

        batch_insert(conn_reporting,
            """INSERT INTO report_planned_works
               (id, task_id, project_id, user_id, work_type, planned_hours, updated_at)
               VALUES %s""",
            buf_report_planned, dry_run)

        batch_insert(conn_reporting,
            """INSERT INTO report_booked_works
               (id, task_id, project_id, user_id, work_type, booked_hours, updated_at, deleted_at)
               VALUES %s""",
            buf_report_booked, dry_run)

        # ── 12. Insert audit_db rows ───────────────────────────────────────────
        batch_insert(conn_audit,
            """INSERT INTO audit_records
               (id, task_id, assigned_user_id, from_status, to_status, changed_at, recorded_at)
               VALUES %s""",
            buf_audit_records, dry_run)

        batch_insert(conn_audit,
            """INSERT INTO phase_audit_records
               (id, task_id, changed_by_user_id, from_phase_id, from_phase_name,
                to_phase_id, to_phase_name, changed_at, recorded_at)
               VALUES %s""",
            buf_phase_audit, dry_run)

    # ── Summary ────────────────────────────────────────────────────────────────
    elapsed = time.perf_counter() - start_time
    print("\n" + "=" * 60)
    print(f"Seed complete in {elapsed:.1f}s")
    print(f"  tasks:        {total_tasks:>10,}")
    print(f"  comments:     {total_comments:>10,}")
    print(f"  participants: {total_participants:>10,}")
    print(f"  planned_works:{total_planned:>10,}")
    print(f"  booked_works: {total_booked:>10,}")
    print(f"  timelines:    {total_timelines:>10,}")
    print(f"  attachments:  {total_attachments:>10,}")
    print(f"  (file_metadata and reporting/audit rows match attachments/works)")
    print("=" * 60)

    if not dry_run:
        print("\nVerify with:")
        print("  docker exec -i ms-postgres psql -U task_svc -d task_db \\")
        print("    -c \"SELECT p.name, COUNT(t.id) FROM tasks t JOIN task_phases ph ON t.phase_id = ph.id JOIN task_projects p ON t.project_id = p.id GROUP BY p.name;\"")


# ─────────────────────────────────────────────────────────────────────────────
# Section F — Entry point
# ─────────────────────────────────────────────────────────────────────────────

def main() -> None:
    """Parse CLI arguments and invoke the seed orchestrator."""
    parser = argparse.ArgumentParser(
        description="Seed 10k tasks (and related data) into PostgreSQL for performance testing.",
    )
    parser.add_argument("--host", default="localhost", help="PostgreSQL host (default: localhost)")
    parser.add_argument("--port", type=int, default=5432, help="PostgreSQL port (default: 5432)")
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Print what would be inserted without writing to the DB",
    )
    args = parser.parse_args()

    if args.dry_run:
        print("[DRY RUN] No data will be written to the database.\n")

    seed(args)


if __name__ == "__main__":
    main()
