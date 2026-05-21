import sys
import json
import os
import re
sys.path.insert(0, os.path.dirname(__file__))
from utils import find_active_feature

# ─────────────────────────────────────────────
# HELPERS
# ─────────────────────────────────────────────

def find_repo_root():
    """Walk up from cwd until we find ARCHITECTURE.md or pom.xml."""
    current = os.getcwd()
    while True:
        if os.path.exists(os.path.join(current, "ARCHITECTURE.md")) or \
           os.path.exists(os.path.join(current, "pom.xml")):
            return current
        parent = os.path.dirname(current)
        if parent == current:
            return os.getcwd()
        current = parent

def read_architecture(repo_root):
    path = os.path.join(repo_root, "ARCHITECTURE.md")
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if len(content) > 8000:
        return content[:8000] + "\n\n[ARCHITECTURE.md truncated — read full file for complete audit]"
    return content

def read_flyway_migrations(repo_root):
    """
    Read all Flyway migration files from src/main/resources/db/migration/
    and extract table definitions, column names, and constraints.
    """
    migration_dir = os.path.join(
        repo_root, "src", "main", "resources", "db", "migration"
    )

    if not os.path.exists(migration_dir):
        return None, None

    sql_files = sorted([
        f for f in os.listdir(migration_dir)
        if f.endswith(".sql")
    ])

    if not sql_files:
        return None, None

    all_sql = []
    table_summary = {}

    for filename in sql_files:
        filepath = os.path.join(migration_dir, filename)
        with open(filepath, "r", encoding="utf-8") as f:
            sql = f.read()
        all_sql.append(f"-- File: {filename}\n{sql}")

        create_blocks = re.findall(
            r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\(([^;]+)\)',
            sql,
            re.IGNORECASE | re.DOTALL
        )
        for table_name, columns_block in create_blocks:
            columns = []
            for line in columns_block.split("\n"):
                line = line.strip().rstrip(",")
                if line and not line.upper().startswith(
                    ("PRIMARY", "FOREIGN", "UNIQUE", "INDEX", "CONSTRAINT", "CHECK")
                ):
                    columns.append(line)
            table_summary[table_name.lower()] = {
                "defined_in": filename,
                "columns": columns
            }

        alter_adds = re.findall(
            r'ALTER\s+TABLE\s+(\w+)\s+ADD\s+(?:COLUMN\s+)?(\w+)',
            sql,
            re.IGNORECASE
        )
        for table_name, col_name in alter_adds:
            tname = table_name.lower()
            if tname not in table_summary:
                table_summary[tname] = {"defined_in": filename, "columns": []}
            table_summary[tname]["columns"].append(
                f"{col_name} (added in {filename})"
            )

    return table_summary, "\n\n".join(all_sql)

def format_schema_summary(table_summary):
    if not table_summary:
        return "No tables found in migration files."
    lines = ["Existing tables in this database:\n"]
    for table, info in sorted(table_summary.items()):
        lines.append(f"  TABLE: {table}  (defined in {info['defined_in']})")
        for col in info["columns"]:
            lines.append(f"    - {col}")
        lines.append("")
    return "\n".join(lines)

def check_existing_plans(repo_root, prompt):
    """
    Scan the plans/ directory to see if a plan already exists
    for something related to this prompt. Also returns all existing
    plan folder names so Claude knows what has already been planned.
    """
    plans_dir = os.path.join(repo_root, "plans")
    if not os.path.exists(plans_dir):
        return {}

    existing_plans = {}
    for folder in os.listdir(plans_dir):
        folder_path = os.path.join(plans_dir, folder)
        if not os.path.isdir(folder_path):
            continue

        found_files = {
            "IMPLEMENTATION_PLAN.md": os.path.exists(
                os.path.join(folder_path, "IMPLEMENTATION_PLAN.md")
            ),
            "TEST_PLAN.md": os.path.exists(
                os.path.join(folder_path, "TEST_PLAN.md")
            ),
            "DIAGRAMS.md": os.path.exists(
                os.path.join(folder_path, "DIAGRAMS.md")
            ),
        }
        existing_plans[folder] = found_files

    return existing_plans

def format_plans_summary(existing_plans):
    if not existing_plans:
        return "No existing plans found in plans/ directory."

    lines = ["Existing feature plans:\n"]
    for folder, files in sorted(existing_plans.items()):
        all_present = all(files.values())
        missing = [f for f, exists in files.items() if not exists]
        status = "COMPLETE" if all_present else f"INCOMPLETE — missing: {', '.join(missing)}"
        lines.append(f"  plans/{folder}/  [{status}]")
        for filename, exists in files.items():
            mark = "✓" if exists else "✗"
            lines.append(f"    {mark} {filename}")
        lines.append("")
    return "\n".join(lines)

# ─────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────

def main():
    try:
        raw = sys.stdin.read()
        data = json.loads(raw)
    except (json.JSONDecodeError, ValueError) as e:
        print(f"Hook error: failed to parse stdin — {e}", file=sys.stderr)
        sys.exit(2)

    try:
        prompt = data.get("prompt", "").lower()

        code_keywords = [
            "feature", "service", "repository", "entity", "table", "store",
            "create", "build", "write", "implement", "add", "endpoint",
            "controller", "model", "class", "method", "column", "field",
            "persist", "save", "insert", "schema", "migration", "jpa",
            "hibernate", "spring", "rest", "api", "fix", "update",
            "refactor", "delete", "remove", "patch", "modify", "rename",
            "move", "extract", "generate", "produce", "scaffold",
            "complete", "make", "code"
        ]

        if not any(kw in prompt for kw in code_keywords):
            sys.exit(0)

        repo_root = find_repo_root()

        # ── 1. Architecture audit ──────────────────
        architecture = read_architecture(repo_root)

        # ── 2. Schema audit ───────────────────────
        table_summary, raw_sql = read_flyway_migrations(repo_root)
        schema_summary = format_schema_summary(table_summary)

        # ── 3. Plans audit ────────────────────────
        feature_name = find_active_feature(repo_root)
        existing_plans = check_existing_plans(repo_root, prompt)
        plans_summary = format_plans_summary(existing_plans)

        # ── 4. Build injected context ─────────────
        context_parts = []

        context_parts.append("""
═══════════════════════════════════════════════════
MANDATORY PRE-CODE AUDIT — DO NOT SKIP
═══════════════════════════════════════════════════

Before writing any code you must complete all three audits below
and deliver the required report. Implementation code is strictly
forbidden until all three audits are complete, all three plan
documents exist, and the user has confirmed how to proceed.
""")

        # ── Architecture ──
        if architecture:
            context_parts.append(f"""
───────────────────────────────────────────────────
AUDIT 1 OF 3: ARCHITECTURE
───────────────────────────────────────────────────

Read the following ARCHITECTURE.md and identify:
- Any existing service, repository, controller, or component that
  overlaps with what the user is asking you to build
- Any existing patterns or abstractions that should be extended
  rather than duplicated

{architecture}
""")
        else:
            context_parts.append("""
AUDIT 1 OF 3: ARCHITECTURE
WARNING: ARCHITECTURE.md not found. Inform the user before proceeding.
""")

        # ── Schema ──
        context_parts.append(f"""
───────────────────────────────────────────────────
AUDIT 2 OF 3: DATABASE SCHEMA
───────────────────────────────────────────────────

The following tables and columns already exist in this PostgreSQL database
based on the Flyway migration files in src/main/resources/db/migration/.

Before suggesting any new table, column, or entity:
- Check whether the data the user wants to store already has a home
  in an existing table
- Check whether an existing column could be reused or repurposed
- If a new migration IS needed, confirm it does not duplicate
  an existing table or column name

{schema_summary}
""")

        # ── Plans ──
        context_parts.append(f"""
───────────────────────────────────────────────────
AUDIT 3 OF 3: FEATURE PLANS
───────────────────────────────────────────────────

You are FORBIDDEN from writing any implementation code until all three
of the following plan documents exist for this feature:

  plans/<feature-name>/IMPLEMENTATION_PLAN.md
  plans/<feature-name>/TEST_PLAN.md
  plans/<feature-name>/DIAGRAMS.md

These are not optional. They are a hard gate.

{plans_summary}

INSTRUCTIONS:
1. Determine an appropriate kebab-case feature name based on the
   user's request (e.g. "user-authentication", "transaction-export")
2. Check the existing plans above to see if a matching or related
   plan folder already exists
3. If all three documents already exist for this feature, state that
   clearly and confirm you are cleared to proceed
4. If any of the three documents are missing, you must create them
   before writing any implementation code — in this order:
     a. plans/<feature-name>/IMPLEMENTATION_PLAN.md
     b. plans/<feature-name>/TEST_PLAN.md
     c. plans/<feature-name>/DIAGRAMS.md
5. After creating all three, present them to the user for review
   before writing a single line of implementation code
""")

        # ── Required report format ──
        context_parts.append("""
───────────────────────────────────────────────────
REQUIRED REPORT FORMAT
───────────────────────────────────────────────────

Respond with this structure. No implementation code until step 4 is confirmed.

ARCHITECTURE AUDIT
  Overlap found: YES / NO / PARTIAL
  If yes or partial: list every affected class, service,
  repository, or component with file paths if known.

DATABASE AUDIT
  Overlap found: YES / NO / PARTIAL
  If yes or partial: list every affected table and column
  that already stores or could store the requested data.

PLAN AUDIT
  Feature name: <the kebab-case name you have chosen>
  Plan status: COMPLETE / INCOMPLETE / NOT STARTED
  If incomplete or not started: list exactly which documents
  are missing and confirm you will create them now.

RECOMMENDATION
  One of:
  A) Extend existing code/schema — describe what to modify
  B) New code/schema justified — explain why it is warranted
  C) Refactor recommended — describe the unified approach

DECISION REQUIRED
  If plans are missing: "I will now create the missing plan
  documents. Please review them before I write any code."
  If plans are complete: "Plans confirmed. Please confirm
  option A, B, or C and I will begin implementation."

═══════════════════════════════════════════════════
""")

        final_context = "\n".join(context_parts)

        # ── Determine if we should hard block or just inject context ──────────
        hard_block = False
        block_reasons = []

        # Hard block if no active feature can be determined
        if not feature_name:
            hard_block = True
            block_reasons.append(
                "BLOCKED — NO ACTIVE FEATURE FOUND\n"
                "  Could not determine the active feature. Either:\n"
                "  - plans/.active-feature does not exist, or\n"
                "  - It points to a folder that does not exist under plans/\n"
                "  Create plans/.active-feature containing your current feature "
                "name before writing any code."
            )
        else:
            # Only check the active feature folder — not all folders under plans/
            active_plan = existing_plans.get(feature_name, {})

            if not active_plan:
                hard_block = True
                block_reasons.append(
                    f"BLOCKED — NO PLAN FOUND FOR ACTIVE FEATURE: {feature_name}\n"
                    f"  plans/{feature_name}/ exists in .active-feature but was not "
                    f"found in plans/.\n"
                    f"  Create the following before writing any code:\n"
                    f"    plans/{feature_name}/IMPLEMENTATION_PLAN.md\n"
                    f"    plans/{feature_name}/TEST_PLAN.md\n"
                    f"    plans/{feature_name}/DIAGRAMS.md"
                )
            else:
                missing = [f for f, exists in active_plan.items() if not exists]
                if missing:
                    hard_block = True
                    block_reasons.append(
                        f"BLOCKED — INCOMPLETE PLAN: plans/{feature_name}/\n"
                        f"  Missing: {', '.join(missing)}\n"
                        f"  All three plan documents must exist before writing any code."
                    )

        if hard_block:
            print("\n\n".join(block_reasons), file=sys.stderr)
            sys.exit(2)

        print(json.dumps({"additionalContext": final_context}))
        sys.exit(0)

    except Exception as e:
        print(
            f"Hook error: unexpected exception — {type(e).__name__}: {e}\n"
            "The hook could not complete. Blocking to prevent unverified "
            "code from proceeding.",
            file=sys.stderr
        )
        sys.exit(2)


if __name__ == "__main__":
    main()