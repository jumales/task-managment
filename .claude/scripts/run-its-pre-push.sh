#!/usr/bin/env bash
# run-its-pre-push.sh
#
# PreToolUse hook — intercepts Bash tool calls where git push or gh pr create
# appears as an actual command (at line start or after &&, ||, ;) and runs local
# integration tests for affected service modules before allowing the action.
#
# Exit 0  → tool call proceeds normally
# Exit 2  → tool call is BLOCKED (tests failed)

# Read tool input and extract the bash command
INPUT=$(cat 2>/dev/null || true)
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // ""' 2>/dev/null || true)

# Only trigger when git push or gh pr create appear as actual commands,
# not inside commit messages or other string arguments.
# Pattern: must be at line start OR after && / || / ;
if ! echo "$CMD" | grep -qE '(^|&&|\|\||;)[[:space:]]*(git push|gh pr create)'; then
  exit 0
fi

# Verify Docker is available (Testcontainers requirement)
if ! docker info > /dev/null 2>&1; then
  echo "⚠️  Docker is not running — skipping local IT run (tests will still run on CI)."
  exit 0
fi

# Detect which service modules changed relative to main
cd /Users/admin/projects/cc/task-managment || exit 0

CHANGED=$(git diff --name-only origin/main...HEAD 2>/dev/null || true)
if [ -z "$CHANGED" ]; then
  CHANGED=$(git diff --name-only HEAD~1..HEAD 2>/dev/null || true)
fi

if [ -z "$CHANGED" ]; then
  echo "No changes detected — skipping IT run."
  exit 0
fi

RUN_USER=false
RUN_TASK=false

if echo "$CHANGED" | grep -qE '^user-service/'; then RUN_USER=true; fi
if echo "$CHANGED" | grep -qE '^task-service/'; then RUN_TASK=true; fi
if echo "$CHANGED" | grep -qE '^common/'; then RUN_USER=true; RUN_TASK=true; fi

MODULES=""
if [ "$RUN_USER" = "true" ]; then MODULES="user-service"; fi
if [ "$RUN_TASK" = "true" ]; then
  if [ -n "$MODULES" ]; then MODULES="$MODULES,task-service"; else MODULES="task-service"; fi
fi

if [ -z "$MODULES" ]; then
  echo "No IT-tested service modules affected — skipping IT run."
  exit 0
fi

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Running local integration tests before push...      ║"
echo "║  Modules: $MODULES"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

if mvn test -pl "$MODULES" -Deureka.client.enabled=false 2>&1; then
  echo ""
  echo "✅ Integration tests passed for [$MODULES] — proceeding."
  exit 0
else
  echo ""
  echo "BLOCK: Integration tests FAILED for [$MODULES]."
  echo "Fix the failing tests before pushing. Run locally:"
  echo "  mvn test -pl $MODULES -Deureka.client.enabled=false"
  exit 2
fi
