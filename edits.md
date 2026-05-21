# Hook Review Report

---

## `.claude/hooks/audit_before_code.py`

### 1. Holes / Loopholes

**Never actually blocks.** The hook always exits 0 regardless of what it finds. It injects context (a warning message) but cannot stop code from being written. If the injected context is ignored, all plan requirements are bypassed entirely.

**Keyword matching is too narrow.** The trigger list misses common coding verbs: `fix`, `update`, `refactor`, `delete`, `remove`, `patch`, `modify`, `rename`, `move`, `extract`. A prompt like "fix the balance service" or "refactor PlaidLinkService" skips the hook entirely.

**Crash when `plans/` directory does not exist.** `check_existing_plans` returns the tuple `([], {})` when `plans/` is absent. The caller assigns this tuple to `existing_plans`, then passes it to `format_plans_summary`, which calls `existing_plans.items()` on it. Tuples have no `.items()` method — this raises `AttributeError`. Claude Code treats the crash as a non-blocking failure and silently proceeds.

**Crash on malformed stdin.** `json.loads(raw)` has no try/except. If Claude Code passes unexpected input, the script crashes and exits non-zero, which is treated as non-blocking.

**ARCHITECTURE.md is truncated at 8,000 characters.** `read_architecture` caps the injected content. If the file grows past that, Claude only sees a partial view with a truncation notice — the audit is incomplete.

**Hardcoded line number references become stale.** The injected message cites `ARCHITECTURE.md (lines 262–276)`. Those rules have now been removed from ARCHITECTURE.md and live only in the hooks. The line references are already wrong and will mislead if ever read.

---

### 2. Exact Conditions for a Clean Exit 0

This hook **always** exits 0. There is no blocking path. Clean exit occurs under any of these (all non-exclusive):

- The user's prompt contains none of the `code_keywords` → hook skips all logic and exits 0
- The user's prompt contains a code keyword → hook runs, injects context, and **still** exits 0

There is no scenario where this hook produces exit 2 or blocks Claude.

---

### 3. Conflicts with Other Hooks

No direct conflicts. Because `audit_before_code.py` never blocks, it cannot conflict with the other hooks. However, there is a **coverage gap**: this hook fires on the user's *prompt*, while `enforce_during_implementation.py` fires on the *tool call*. A prompt that contains no keywords but causes Claude to write a Java file will skip audit entirely while still triggering the implementation hook.

---
---

## `.claude/hooks/enforce_during_implementation.py`

### 1. Holes / Loopholes

**Entity classes are not in the `NO_TEST_REQUIRED` exclusion list.** The list excludes `*Application.java`, `*Config.java`, `*Exception.java`, `*Dto.java`, `*Request.java`, `*Response.java`, and `*Mapper.java` — but NOT `*Entity.java`. Creating an entity file (e.g. `PlaidItem.java`) triggers Block 2 and requires a test file at `src/test/java/.../PlaidItemTest.java`. This conflicts with the coverage policy in `definition_of_done.py`, which excludes entity classes from the 90% threshold. The result: you must create an entity test file to satisfy this hook, but that test file contributes nothing meaningful to coverage.

**Active feature is determined by most-recently-modified plan folder.** `find_feature_name` picks whichever plan folder was touched most recently. In a multi-feature repo (or when going back to fix an older feature), it will pick the wrong feature. The TEST_PLAN.md it checks — and the feature name it reports in block messages — may belong to an unrelated feature.

**Block 2 is skipped entirely when Block 1 fires.** Line 189: `if not blocks and requires_test(file_path)`. If TEST_PLAN.md is missing, the test-file check is never evaluated. You will not be told about the missing test file until after you fix the missing plan.

**`json.loads(raw)` has no error handling.** Malformed stdin crashes the hook. An unhandled exception exits non-zero, which Claude Code treats as non-blocking.

**An empty test file satisfies the hook.** Block 2 checks only that the expected test file exists on disk (`os.path.exists`). A zero-byte file, a file with a single comment, or a file with a stub test class that tests nothing will all satisfy the check.

**`file_path` is empty → immediate exit 0.** If a tool is called without a file path in the input, the hook exits 0 unconditionally. Any tool that writes a Java file but names its parameter something other than `file_path`, `path`, or `new_path` bypasses the hook.

**Crash on malformed stdin.** Same as audit hook — `json.loads(raw)` is unguarded.

---

### 2. Exact Conditions for a Clean Exit 0

The hook exits 0 (no block) when **any** of these early-exit conditions is true:

- `file_path` extracted from tool input is empty → exit 0
- `file_path` does not contain `src/main/java` or does not end with `.java` → exit 0
- `file_path` contains `src/test/java` (it is a test file) → exit 0

If none of those apply, a clean exit 0 requires **all** of the following:

1. A plan folder with `IMPLEMENTATION_PLAN.md` exists in `plans/` (so `find_feature_name` returns a non-None result)
2. `TEST_PLAN.md` exists inside that plan folder
3. Either the file being written matches one of the `NO_TEST_REQUIRED` patterns, OR the expected test file already exists on disk at `src/test/java/<mirrored-path>/<ClassName>Test.java`

If all three are satisfied and the implementation file already exists (modification case), a non-blocking warning is injected into context but the hook still exits 0.

---

### 3. Conflicts with Other Hooks

**Conflict with `definition_of_done.py` on entity classes.** `enforce_during_implementation.py` requires a test file for entity classes (not in `NO_TEST_REQUIRED`). `definition_of_done.py` excludes entity classes from the 90% coverage threshold. You must create an entity test file to pass `enforce_during_implementation`, but the test adds no coverage value and definition_of_done will not penalize you for its absence.

**No conflict with `audit_before_code.py`.** Audit never blocks, so it cannot create a state that makes the implementation hook unsatisfiable.

---
---

## `.claude/hooks/definition_of_done.py`

### 1. Holes / Loopholes

**CRITICAL — IndentationError: the script is broken and never runs.** Starting at line 733, the code switches from 4-space indentation (used throughout `main()`) to 3-space indentation. Python raises `IndentationError` at parse time, before any logic executes. The process exits non-zero without printing to stderr, so Claude Code treats it as a non-blocking failure and silently proceeds. **The definition-of-done hook has never enforced anything.**

**Silent exit when no active feature exists.** If `find_active_feature` returns `None` (no plan folder with `IMPLEMENTATION_PLAN.md` exists), the hook exits 0 with no message. Any session that has not run the planning phase bypasses all DoD checks.

**Coverage block is suppressed when `jacoco.xml` is missing.** `parse_jacoco_coverage` returns `(None, None, [], [])` if the report file doesn't exist. `coverage_ok` is set to `False`, but the coverage block message is only appended when `overall_line is not None` (line 704). If JaCoCo never generated a report (e.g. `mvn test` failed before it ran, or JaCoCo is not configured), the hook will not block on missing coverage data — it will only block on the test failure itself.

**`mvn` not on PATH produces a misleading block.** If `mvn` is not found, `run_maven_tests` returns `(False, "mvn not found on PATH", -1)`. The hook then fires a block message saying "TEST FAILURES DETECTED — -1 failure(s)" rather than reporting that the build tool was unavailable.

**`run_jacoco_report` silently swallows all errors.** The `except FileNotFoundError: pass` in `run_jacoco_report` hides any failure to generate the JaCoCo report. No warning is surfaced.

**`run_maven_tests` has no timeout.** `subprocess.run` without `timeout=` can hang indefinitely if the build hangs.

**"ARCHITECTURE.MD OVERRIDE" is a designed bypass.** The stale-documentation block includes explicit instructions for how to override it: say `ARCHITECTURE.MD OVERRIDE: ComponentName — non-functional change`. This is intentional but is a loophole to document.

**Active feature heuristic (most-recently-modified plan folder) can pick the wrong feature** — same issue as in `enforce_during_implementation.py`.

**`json.loads(raw)` unguarded.** Same crash vulnerability as the other hooks.

**TEST_REPORT.md is written even when the hook will block.** The report is created at step 4 regardless of whether tests pass. A failing TEST_REPORT.md sitting in `testing/` could be mistaken for a completed run.

---

### 2. Exact Conditions for a Clean Exit 0

Due to the IndentationError, the script currently **never** reaches exit 0 — it crashes at parse time every time it is invoked.

Once the indentation is fixed, exit 0 would require **all** of the following to be true simultaneously:

1. `find_active_feature` returns a non-None feature name (at least one plan folder with `IMPLEMENTATION_PLAN.md` exists)
2. `mvn test` exits with return code 0 and the Maven output contains zero `Failures:` and zero `Errors:`
3. `jacoco.xml` exists and, after excluding classes matching `COVERAGE_EXCLUSIONS`, overall line coverage ≥ 90% and overall branch coverage ≥ 90%
4. Every new Java component (untracked file under `src/main/java`) has its class name mentioned in `documentation/ARCHITECTURE.md`
5. Every modified Java component (tracked file under `src/main/java`) either: (a) has its class name mentioned in ARCHITECTURE.md and ARCHITECTURE.md was also modified in this git session, OR (b) the user has explicitly issued an `ARCHITECTURE.MD OVERRIDE: ComponentName — non-functional change` statement

---

### 3. Conflicts with Other Hooks

**Conflict with `enforce_during_implementation.py` on entity classes** (same as described above). `enforce_during_implementation.py` blocks writing an entity file until a corresponding `*EntityTest.java` exists. `definition_of_done.py`'s `COVERAGE_EXCLUSIONS` excludes entity classes from coverage. The entity test file is required to write the entity, but meaningless to the DoD coverage check.

**Potential ordering conflict on ARCHITECTURE.md updates.** `definition_of_done.py` requires ARCHITECTURE.md to have been modified in the current git session for modified components to pass. But `enforce_during_implementation.py` fires before ARCHITECTURE.md is written and does not check ARCHITECTURE.md at all. There is no hook that prevents Claude from writing implementation code *before* updating ARCHITECTURE.md — `definition_of_done.py` is the only gate for that, and it is currently broken.