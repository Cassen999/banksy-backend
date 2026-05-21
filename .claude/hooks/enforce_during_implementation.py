import sys
import json
import os
sys.path.insert(0, os.path.dirname(__file__))
from utils import find_active_feature
import re

# ─────────────────────────────────────────────
# CONSTANTS
# ─────────────────────────────────────────────

# Maps implementation paths to their expected test counterparts
IMPL_ROOT = os.path.join("src", "main", "java")
TEST_ROOT = os.path.join("src", "test", "java")

# Files that are never expected to have a direct test counterpart
NO_TEST_REQUIRED = [
    r".*Application\.java$",         # Spring Boot entry point
    r".*Config\.java$",              # Configuration classes
    r".*Configuration\.java$",
    r".*Exception\.java$",           # Exception classes
    r".*Constants\.java$",           # Constants
    r".*Properties\.java$",          # Property binding classes
    r".*Dto\.java$",                 # DTOs (covered by service tests)
    r".*Request\.java$",             # Request models
    r".*Response\.java$",            # Response models
    r".*Mapper\.java$",              # MapStruct mappers (optional — remove if you test these)
    r".*Entity\.java$",
]

# ─────────────────────────────────────────────
# HELPERS
# ─────────────────────────────────────────────

def find_repo_root():
    current = os.getcwd()
    while True:
        if os.path.exists(os.path.join(current, "pom.xml")):
            return current
        parent = os.path.dirname(current)
        if parent == current:
            return os.getcwd()
        current = parent

def is_implementation_file(file_path):
    """Returns True if this is a Java source file under src/main/java."""
    normalized = file_path.replace("\\", "/")
    return (
        "src/main/java" in normalized
        and normalized.endswith(".java")
    )

def is_test_file(file_path):
    """Returns True if this is already a test file."""
    normalized = file_path.replace("\\", "/")
    return "src/test/java" in normalized

def requires_test(file_path):
    """Returns False for files that are exempt from direct test requirements."""
    for pattern in NO_TEST_REQUIRED:
        if re.search(pattern, file_path):
            return False
    return True

def derive_expected_test_path(impl_path, repo_root):
    """
    Given an implementation file path, derive the expected test file path.
    e.g. src/main/java/org/example/service/UserService.java
      -> src/test/java/org/example/service/UserServiceTest.java
    """
    normalized = impl_path.replace("\\", "/")

    # Strip repo root prefix if present
    if repo_root:
        repo_normalized = repo_root.replace("\\", "/")
        if normalized.startswith(repo_normalized):
            normalized = normalized[len(repo_normalized):].lstrip("/")

    if "src/main/java/" not in normalized:
        return None

    relative = normalized.split("src/main/java/", 1)[1]
    # e.g. org/example/service/UserService.java
    base = relative.replace(".java", "")
    test_relative = base + "Test.java"
    return os.path.join(repo_root, "src", "test", "java", test_relative)

def test_file_exists(expected_test_path):
    """
    Check if the test file exists on disk AND contains at least
    one @Test annotation — an empty or stub file does not count.
    """
    if not expected_test_path:
        return False
    if not os.path.exists(expected_test_path):
        return False
    try:
        with open(expected_test_path, "r", encoding="utf-8") as f:
            content = f.read()
        return bool(re.search(
            r'@Test|@ParameterizedTest|@RepeatedTest|@TestFactory',
            content
        ))
    except (IOError, OSError):
        return False

def check_test_plan_exists(test_plan_path):
    if not test_plan_path:
        return False
    return os.path.exists(test_plan_path)

def is_modifying_existing_file(file_path, repo_root):
    """Returns True if the file already exists (modification vs new file)."""
    full_path = file_path if os.path.isabs(file_path) else os.path.join(repo_root, file_path)
    return os.path.exists(full_path)

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
        tool_name = data.get("tool_name", "")
        tool_input = data.get("tool_input", {})

        # Get the file path from whichever tool is firing
        file_path = (
            tool_input.get("file_path")
            or tool_input.get("path")
            or tool_input.get("new_path")
            or ""
        )

        if not file_path:
            sys.exit(0)

        # Only care about Java implementation files
        if not is_implementation_file(file_path):
            sys.exit(0)

        repo_root = find_repo_root()
        feature_name = find_active_feature(repo_root)
        test_plan_path = os.path.join(repo_root, "plans", feature_name, "TEST_PLAN.md") if feature_name else None
        is_modification = is_modifying_existing_file(file_path, repo_root)
        has_test_plan = check_test_plan_exists(test_plan_path)

        blocks = []
        warnings = []

        # ── BLOCK 1: TEST_PLAN.md missing ─────────────────────────────────────
        if not has_test_plan:
            if feature_name:
                blocks.append(
                    f"BLOCKED: plans/{feature_name}/TEST_PLAN.md does not exist.\n"
                    f"Per ARCHITECTURE.md and TESTING_POLICY.md, you must not write "
                    f"implementation code without a TEST_PLAN.md. Create "
                    f"plans/{feature_name}/TEST_PLAN.md first and have the user "
                    f"review it before proceeding."
                )
            else:
                blocks.append(
                    "BLOCKED: No active feature plan found in plans/ directory.\n"
                    "Per ARCHITECTURE.md, a feature plan with TEST_PLAN.md must "
                    "exist before writing implementation code. Return to the "
                    "planning phase."
                )

        # ── BLOCK 2: Test file missing for this implementation file ───────────
        if not blocks and requires_test(file_path):
            expected_test = derive_expected_test_path(file_path, repo_root)
            test_exists = test_file_exists(expected_test)

            if not test_exists:
                rel_test = expected_test.replace(repo_root, "").lstrip("/\\") if expected_test else "unknown"

                if is_modification:
                    blocks.append(
                        f"BLOCKED: You are modifying {file_path} but its corresponding "
                        f"test file does not exist:\n"
                        f"  Expected: {rel_test}\n\n"
                        f"Per ARCHITECTURE.md: all modified code must have its existing "
                        f"tests updated. You must create {rel_test} before modifying "
                        f"this file, or confirm this class was previously untested and "
                        f"create the test file now."
                    )
                else:
                    blocks.append(
                        f"BLOCKED: You are creating {file_path} without a corresponding "
                        f"test file.\n"
                        f"  Expected: {rel_test}\n\n"
                        f"Per ARCHITECTURE.md and TESTING_POLICY.md: all new code must "
                        f"have tests written alongside or before implementation.\n\n"
                        f"You must write {rel_test} before or immediately after this "
                        f"file. Write the test file first, then return to this file."
                    )

        # ── WARNING: Modifying impl without touching tests ─────────────────────
        if not blocks and is_modification and requires_test(file_path):
            expected_test = derive_expected_test_path(file_path, repo_root)
            if expected_test and test_file_exists(expected_test):
                warnings.append(
                    f"REMINDER: You are modifying {os.path.basename(file_path)}. "
                    f"Per ARCHITECTURE.md, its existing tests must also be reviewed "
                    f"and updated if behavior has changed. Ensure "
                    f"{os.path.basename(expected_test)} reflects this change."
                )

        # ── Hard block — exit 2 surfaces message to Claude as an error ────────
        if blocks:
            print("\n".join(blocks), file=sys.stderr)
            sys.exit(2)

        # ── Soft warning — injected as context, not a block ───────────────────
        if warnings:
            print(json.dumps({"additionalContext": "\n".join(warnings)}))

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