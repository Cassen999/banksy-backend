import sys
import json
import os
import re
sys.path.insert(0, os.path.dirname(__file__))
from utils import find_active_feature

# ─────────────────────────────────────────────
# CONSTANTS
# ─────────────────────────────────────────────

REQUIRED_PLAN_FILES = [
    "IMPLEMENTATION_PLAN.md",
    "TEST_PLAN.md",
    "DIAGRAMS.md",
]

APPROVAL_MARKER = ".approved"

# Words the user must say to grant approval to begin writing code.
APPROVAL_KEYWORDS = [
    "approved",
    "approve",
    "lgtm",
    "looks good",
    "go ahead",
    "proceed",
    "ship it",
    "good to go",
    "confirmed",
    "confirm",
    "happy with",
    "commence",
    "start implementation",
    "begin implementation",
    "start coding",
    "begin coding",
    "you may",
]

# Files that are never expected to have a direct test counterpart
NO_TEST_REQUIRED = [
    r".*Application\.java$",
    r".*Config\.java$",
    r".*Configuration\.java$",
    r".*Exception\.java$",
    r".*Constants\.java$",
    r".*Properties\.java$",
    r".*Dto\.java$",
    r".*Request\.java$",
    r".*Response\.java$",
    r".*Mapper\.java$",
    r".*Entity\.java$",
    r".*/entity/.*\.java$",   # all classes in the entity package (consistent with coverage exclusions)
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

def is_plans_file(file_path):
    """Returns True if the file lives under the plans/ directory."""
    normalized = file_path.replace("\\", "/")
    # Absolute path: check if /plans/ appears after the repo root segment
    # Relative path: starts with plans/
    return "/plans/" in normalized or normalized.startswith("plans/")

def missing_plan_files(repo_root, feature_name):
    """Returns a list of plan file names that do not yet exist on disk."""
    missing = []
    for fname in REQUIRED_PLAN_FILES:
        path = os.path.join(repo_root, "plans", feature_name, fname)
        if not os.path.exists(path):
            missing.append(fname)
    return missing

def approval_marker_exists(repo_root, feature_name):
    marker = os.path.join(repo_root, "plans", feature_name, APPROVAL_MARKER)
    return os.path.exists(marker)

def create_approval_marker(repo_root, feature_name):
    marker = os.path.join(repo_root, "plans", feature_name, APPROVAL_MARKER)
    try:
        with open(marker, "w", encoding="utf-8") as f:
            f.write("")
    except Exception:
        pass

def check_transcript_for_approval(data):
    """
    Read the session transcript and return True if the most recent user
    message contains an explicit approval keyword.
    """
    transcript_path = data.get("transcript_path", "")
    if not transcript_path or not os.path.exists(transcript_path):
        return False
    try:
        last_user_text = ""
        with open(transcript_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    entry = json.loads(line)
                    if entry.get("type") != "user":
                        continue
                    content = entry.get("message", {}).get("content", "")
                    if isinstance(content, str):
                        last_user_text = content
                    elif isinstance(content, list):
                        for block in content:
                            if isinstance(block, dict) and block.get("type") == "text":
                                last_user_text = block.get("text", "")
                except (json.JSONDecodeError, KeyError):
                    continue
        msg = last_user_text.strip().lower()
        return any(kw in msg for kw in APPROVAL_KEYWORDS)
    except Exception:
        return False

def is_implementation_file(file_path):
    normalized = file_path.replace("\\", "/")
    return "src/main/java" in normalized and normalized.endswith(".java")

def requires_test(file_path):
    for pattern in NO_TEST_REQUIRED:
        if re.search(pattern, file_path):
            return False
    return True

def derive_expected_test_path(impl_path, repo_root):
    normalized = impl_path.replace("\\", "/")
    if repo_root:
        repo_normalized = repo_root.replace("\\", "/")
        if normalized.startswith(repo_normalized):
            normalized = normalized[len(repo_normalized):].lstrip("/")
    if "src/main/java/" not in normalized:
        return None
    relative = normalized.split("src/main/java/", 1)[1]
    base = relative.replace(".java", "")
    return os.path.join(repo_root, "src", "test", "java", base + "Test.java")

def test_file_exists(expected_test_path):
    if not expected_test_path or not os.path.exists(expected_test_path):
        return False
    try:
        with open(expected_test_path, "r", encoding="utf-8") as f:
            content = f.read()
        return bool(re.search(
            r'@Test|@ParameterizedTest|@RepeatedTest|@TestFactory', content
        ))
    except (IOError, OSError):
        return False

def is_modifying_existing_file(file_path, repo_root):
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
        tool_input = data.get("tool_input", {})
        file_path = (
            tool_input.get("file_path")
            or tool_input.get("path")
            or tool_input.get("new_path")
            or ""
        )

        if not file_path:
            sys.exit(0)

        # ── GATE 0: Planning files are always allowed ─────────────────────────
        if is_plans_file(file_path):
            sys.exit(0)

        repo_root = find_repo_root()
        feature_name = find_active_feature(repo_root)

        # ── GATE 1: Active feature must exist ─────────────────────────────────
        if not feature_name:
            print(
                "BLOCKED — NO ACTIVE FEATURE\n"
                "  plans/.active-feature does not exist or points to a missing folder.\n"
                "  Create plans/.active-feature containing your current feature name,\n"
                "  then create the three required plan documents before writing any code.",
                file=sys.stderr
            )
            sys.exit(2)

        # ── GATE 2: All three plan documents must exist ────────────────────────
        missing = missing_plan_files(repo_root, feature_name)
        if missing:
            missing_list = "\n".join(f"    plans/{feature_name}/{f}" for f in missing)
            print(
                f"BLOCKED — PLAN DOCUMENTS INCOMPLETE\n"
                f"  The following required plan files are missing for feature '{feature_name}':\n"
                f"{missing_list}\n\n"
                f"  You must create ALL THREE plan documents before writing any code:\n"
                f"    plans/{feature_name}/IMPLEMENTATION_PLAN.md\n"
                f"    plans/{feature_name}/TEST_PLAN.md\n"
                f"    plans/{feature_name}/DIAGRAMS.md\n\n"
                f"  After creating them, present them to the user and wait for explicit approval.",
                file=sys.stderr
            )
            sys.exit(2)

        # ── GATE 3: User must have explicitly approved the plans ───────────────
        if not approval_marker_exists(repo_root, feature_name):
            if check_transcript_for_approval(data):
                # User approved — record it so future writes don't re-check
                create_approval_marker(repo_root, feature_name)
            else:
                print(
                    f"BLOCKED — WAITING FOR USER APPROVAL\n"
                    f"  All three plan documents exist for '{feature_name}' but the user\n"
                    f"  has not yet explicitly approved them.\n\n"
                    f"  Present the contents of:\n"
                    f"    plans/{feature_name}/IMPLEMENTATION_PLAN.md\n"
                    f"    plans/{feature_name}/TEST_PLAN.md\n"
                    f"    plans/{feature_name}/DIAGRAMS.md\n\n"
                    f"  Then stop and wait. Do NOT write any code until the user responds\n"
                    f"  with one of: approved, lgtm, looks good, go ahead, proceed,\n"
                    f"  ship it, good to go, confirmed.",
                    file=sys.stderr
                )
                sys.exit(2)

        # ── GATE 4 (Java only): Corresponding test file must exist ─────────────
        if is_implementation_file(file_path) and requires_test(file_path):
            expected_test = derive_expected_test_path(file_path, repo_root)
            if not test_file_exists(expected_test):
                rel_test = (
                    expected_test.replace(repo_root, "").lstrip("/\\")
                    if expected_test else "unknown"
                )
                is_modification = is_modifying_existing_file(file_path, repo_root)
                if is_modification:
                    print(
                        f"BLOCKED: You are modifying {file_path} but its corresponding "
                        f"test file does not exist:\n"
                        f"  Expected: {rel_test}\n\n"
                        f"Create {rel_test} before modifying this file.",
                        file=sys.stderr
                    )
                else:
                    print(
                        f"BLOCKED: You are creating {file_path} without a corresponding "
                        f"test file.\n"
                        f"  Expected: {rel_test}\n\n"
                        f"Write {rel_test} first, then return to this file.",
                        file=sys.stderr
                    )
                sys.exit(2)

        # ── Soft warning: modifying impl without touching its tests ────────────
        if is_implementation_file(file_path) and requires_test(file_path):
            if is_modifying_existing_file(file_path, repo_root):
                expected_test = derive_expected_test_path(file_path, repo_root)
                if expected_test and test_file_exists(expected_test):
                    print(json.dumps({"additionalContext": (
                        f"REMINDER: You are modifying {os.path.basename(file_path)}. "
                        f"Its existing tests must also be reviewed and updated if "
                        f"behavior has changed. Ensure "
                        f"{os.path.basename(expected_test)} reflects this change."
                    )}))

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
