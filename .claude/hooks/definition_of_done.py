import sys
import json
import os
import re
import subprocess
sys.path.insert(0, os.path.dirname(__file__))
from utils import find_active_feature

# ─────────────────────────────────────────────
# CONSTANTS
# ─────────────────────────────────────────────

# JaCoCo coverage threshold
COVERAGE_THRESHOLD = 90.0

# JaCoCo XML report path relative to repo root
JACOCO_REPORT = os.path.join("target", "site", "jacoco", "jacoco.xml")

# Classes excluded from the 90% threshold
# Per plans/Testing/TESTING_POLICY.md (lines 20-30)
COVERAGE_EXCLUSIONS = [
    r"Main$",
    r".*Config$",
    r".*Configuration$",
    r"config/.*",
    r".*Entity$",
    r"entity/.*",
    r".*Exception$",
    r"exception/.*",
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

# ─────────────────────────────────────────────
# MVN TEST
# ─────────────────────────────────────────────

def run_maven_tests(repo_root):
    """
    Run mvn test and return (success: bool, output: str, failure_count: int)
    """
    try:
        result = subprocess.run(
            ["mvn", "test", "-f", os.path.join(repo_root, "pom.xml")],
            capture_output=True,
            text=True,
            cwd=repo_root,
            timeout=300
        )
        output = result.stdout + result.stderr

        # Parse failure/error counts from Maven output
        # e.g. "Tests run: 42, Failures: 0, Errors: 0, Skipped: 0"
        failures = 0
        errors = 0
        for match in re.finditer(
            r"Tests run:\s*\d+,\s*Failures:\s*(\d+),\s*Errors:\s*(\d+)",
            output
        ):
            failures += int(match.group(1))
            errors += int(match.group(2))

        total_failures = failures + errors
        success = result.returncode == 0 and total_failures == 0
        return success, output, total_failures

    except FileNotFoundError:
        return (
            False,
            "ERROR: mvn was not found on PATH. "
            "Ensure Maven is installed and accessible from the terminal "
            "environment where Claude Code is running.",
            -1
        )

    except subprocess.TimeoutExpired:
        return (
            False,
            "ERROR: mvn test timed out after 300 seconds. "
            "The build may be hanging. Check for deadlocks, "
            "infinite loops, or tests that never complete.",
            -1
        )

# ─────────────────────────────────────────────
# JACOCO COVERAGE
# ─────────────────────────────────────────────

def run_jacoco_report(repo_root):
    """Generate JaCoCo XML report via mvn jacoco:report."""
    try:
        subprocess.run(
            ["mvn", "jacoco:report", "-f", os.path.join(repo_root, "pom.xml")],
            capture_output=True,
            text=True,
            cwd=repo_root,
            timeout=300
        )
    except FileNotFoundError:
        pass
    except subprocess.TimeoutExpired:
        print(
            "WARNING: mvn jacoco:report timed out after 300 seconds. "
            "Coverage report may be incomplete.",
            file=sys.stderr
        )


def is_excluded(class_name):
    """Returns True if this class is excluded from the coverage threshold."""
    for pattern in COVERAGE_EXCLUSIONS:
        if re.search(pattern, class_name):
            return True
    return False

def parse_jacoco_coverage(repo_root):
    """
    Parse jacoco.xml and return:
    - overall_line_coverage: float (percentage)
    - overall_branch_coverage: float (percentage)
    - failing_classes: list of dicts with name, line_pct, branch_pct
    - excluded_classes: list of class names that were excluded
    """
    report_path = os.path.join(repo_root, JACOCO_REPORT)

    if not os.path.exists(report_path):
        return None, None, [], []

    with open(report_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Parse per-class coverage
    # <class name="org/example/service/UserService" ...>
    #   <counter type="LINE" missed="2" covered="18"/>
    #   <counter type="BRANCH" missed="1" covered="5"/>
    # </class>
    class_blocks = re.findall(
        r'<class name="([^"]+)"[^>]*>(.*?)</class>',
        content,
        re.DOTALL
    )

    total_line_missed = 0
    total_line_covered = 0
    total_branch_missed = 0
    total_branch_covered = 0
    failing_classes = []
    excluded_classes = []

    for class_path, block in class_blocks:
        # Convert path to simple class name for exclusion matching
        class_name = class_path.split("/")[-1]
        full_name = class_path.replace("/", ".")

        if is_excluded(class_name) or is_excluded(class_path):
            excluded_classes.append(full_name)
            continue

        line_match = re.search(
            r'<counter type="LINE" missed="(\d+)" covered="(\d+)"', block
        )
        branch_match = re.search(
            r'<counter type="BRANCH" missed="(\d+)" covered="(\d+)"', block
        )

        line_missed = int(line_match.group(1)) if line_match else 0
        line_covered = int(line_match.group(2)) if line_match else 0
        branch_missed = int(branch_match.group(1)) if branch_match else 0
        branch_covered = int(branch_match.group(2)) if branch_match else 0

        total_line_missed += line_missed
        total_line_covered += line_covered
        total_branch_missed += branch_missed
        total_branch_covered += branch_covered

        # Check per-class coverage
        line_total = line_missed + line_covered
        branch_total = branch_missed + branch_covered

        line_pct = (line_covered / line_total * 100) if line_total > 0 else 100.0
        branch_pct = (branch_covered / branch_total * 100) if branch_total > 0 else 100.0

        if line_pct < COVERAGE_THRESHOLD or branch_pct < COVERAGE_THRESHOLD:
            failing_classes.append({
                "name": full_name,
                "line_pct": round(line_pct, 1),
                "branch_pct": round(branch_pct, 1)
            })

    # Overall percentages
    total_lines = total_line_missed + total_line_covered
    total_branches = total_branch_missed + total_branch_covered

    overall_line = (
        (total_line_covered / total_lines * 100) if total_lines > 0 else 100.0
    )
    overall_branch = (
        (total_branch_covered / total_branches * 100) if total_branches > 0 else 100.0
    )

    return (
        round(overall_line, 1),
        round(overall_branch, 1),
        failing_classes,
        excluded_classes
    )

# ─────────────────────────────────────────────
# ARCHITECTURE.MD COMPONENT COMPLIANCE
# ─────────────────────────────────────────────

def read_architecture_md(repo_root):
    """Read ARCHITECTURE.md and return its contents."""
    path = os.path.join(repo_root, "documentation", "ARCHITECTURE.md")
    if not os.path.exists(path):
        # Fall back to repo root
        path = os.path.join(repo_root, "ARCHITECTURE.md")
    if not os.path.exists(path):
        return None, None
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    return content, path

def get_git_changed_files(repo_root):
    """
    Get all files changed since the last commit (staged + unstaged + untracked).
    Returns list of file paths relative to repo root.
    """
    try:
        staged = subprocess.run(
            ["git", "diff", "--cached", "--name-only"],
            capture_output=True, text=True, cwd=repo_root
        ).stdout.strip()

        unstaged = subprocess.run(
            ["git", "diff", "--name-only"],
            capture_output=True, text=True, cwd=repo_root
        ).stdout.strip()

        untracked = subprocess.run(
            ["git", "ls-files", "--others", "--exclude-standard"],
            capture_output=True, text=True, cwd=repo_root
        ).stdout.strip()

        all_files = set()
        for block in [staged, unstaged, untracked]:
            for f in block.split("\n"):
                f = f.strip()
                if f:
                    all_files.add(f)

        return list(all_files)

    except FileNotFoundError:
        return []

def get_component_name(file_path):
    """
    Derive a simple component name from a Java file path.
    e.g. src/main/java/org/example/service/UserService.java -> UserService
    """
    basename = os.path.basename(file_path)
    return basename.replace(".java", "")

def get_component_diff(repo_root, file_path):
    """
    Get the git diff for a specific file.
    Covers staged, unstaged, and new untracked files.
    """
    try:
        # Try staged diff first
        result = subprocess.run(
            ["git", "diff", "--cached", "--", file_path],
            capture_output=True, text=True, cwd=repo_root
        )
        if result.stdout.strip():
            return result.stdout.strip()

        # Try unstaged diff
        result = subprocess.run(
            ["git", "diff", "--", file_path],
            capture_output=True, text=True, cwd=repo_root
        )
        if result.stdout.strip():
            return result.stdout.strip()

        # New untracked file — show full content as the diff
        full_path = os.path.join(repo_root, file_path)
        if os.path.exists(full_path):
            with open(full_path, "r", encoding="utf-8") as f:
                content = f.read()
            return f"[NEW FILE]\n{content}"

        return ""
    except FileNotFoundError:
        return ""

def classify_java_changes(changed_files, repo_root):
    """
    Separate changed Java files into:
    - new_components: files that are untracked (new to the repo)
    - modified_components: files that already existed and were changed
    Excludes test files and non-Java files.
    """
    new_components = []
    modified_components = []

    for f in changed_files:
        normalized = f.replace("\\", "/")
        if not normalized.endswith(".java"):
            continue
        if "src/test/java" in normalized:
            continue
        if "src/main/java" not in normalized:
            continue

        component_name = get_component_name(f)
        entry = {"file": f, "component": component_name}

        # Check if this is a new untracked file
        try:
            result = subprocess.run(
                ["git", "ls-files", "--error-unmatch", f],
                capture_output=True, text=True,
                cwd=repo_root
            )
            if result.returncode != 0:
                new_components.append(entry)
            else:
                modified_components.append(entry)
        except FileNotFoundError:
            modified_components.append(entry)

    return new_components, modified_components

def component_described_in_architecture(component_name, arch_content):
    """
    Returns True if the component name appears in ARCHITECTURE.md.
    Checks for the class name as a whole word to avoid partial matches.
    """
    if not arch_content:
        return False
    pattern = r'\b' + re.escape(component_name) + r'\b'
    return bool(re.search(pattern, arch_content, re.IGNORECASE))

def architecture_md_in_changed_files(changed_files):
    """Returns True if ARCHITECTURE.md was updated in this session."""
    return any(
        "ARCHITECTURE.md" in f or "architecture.md" in f.lower()
        for f in changed_files
    )

def build_architecture_compliance_report(
    new_components,
    modified_components,
    arch_content,
    changed_files,
    repo_root
):
    """
    Evaluate every changed Java component against ARCHITECTURE.md and
    return (blocks, context_lines).

    Rules:
    - New component not described in ARCHITECTURE.md → hard block
    - New component already described → pass (unlikely but handle cleanly)
    - Modified component not described in ARCHITECTURE.md → hard block
      (it was never documented — treat same as new)
    - Modified component described in ARCHITECTURE.md but ARCHITECTURE.md
      was NOT updated this session → hard block
    - Modified component described and ARCHITECTURE.md WAS updated → pass
    """
    blocks = []
    passes = []
    arch_was_updated = architecture_md_in_changed_files(changed_files)

    all_components = (
        [(c, "new") for c in new_components] +
        [(c, "modified") for c in modified_components]
    )

    for entry, change_type in all_components:
        component = entry["component"]
        file_path = entry["file"]
        is_described = component_described_in_architecture(component, arch_content)

        if change_type == "new":
            if not is_described:
                blocks.append({
                    "component": component,
                    "file": file_path,
                    "reason": "new_not_documented",
                    "message": (
                        f"NEW COMPONENT NOT DOCUMENTED: `{component}`\n"
                        f"  File: {file_path}\n"
                        f"  This is a new component that does not appear in "
                        f"ARCHITECTURE.md.\n"
                        f"  Per ARCHITECTURE.md (lines 1-3): all new components "
                        f"must be described in ARCHITECTURE.md before this work "
                        f"is considered done.\n"
                        f"  Add a description of `{component}` to "
                        f"documentation/ARCHITECTURE.md following the existing "
                        f"structure already in place in that file."
                    )
                })
            else:
                passes.append(f"`{component}` — new, already described (pass)")

        elif change_type == "modified":
            if not is_described:
                # Was never documented — treat as missing documentation
                blocks.append({
                    "component": component,
                    "file": file_path,
                    "reason": "modified_never_documented",
                    "message": (
                        f"MODIFIED COMPONENT NEVER DOCUMENTED: `{component}`\n"
                        f"  File: {file_path}\n"
                        f"  This component was modified but has no description "
                        f"in ARCHITECTURE.md.\n"
                        f"  Add a description of `{component}` to "
                        f"documentation/ARCHITECTURE.md following the existing "
                        f"structure already in place in that file."
                    )
                })
            elif not arch_was_updated:
                # Described but ARCHITECTURE.md was not touched this session
                # Hard block — all changes hard block per your decision
                blocks.append({
                    "component": component,
                    "file": file_path,
                    "reason": "modified_description_stale",
                    "message": (
                        f"STALE DOCUMENTATION: `{component}`\n"
                        f"  File: {file_path}\n"
                        f"  `{component}` is described in ARCHITECTURE.md but "
                        f"it was changed this session and ARCHITECTURE.md was "
                        f"not updated.\n"
                        f"  ARCHITECTURE.md must always be updated in the same "
                        f"session as any change to a documented component, "
                        f"regardless of whether the change is functional or "
                        f"non-functional.\n"
                        f"  Update the description of `{component}` in "
                        f"documentation/ARCHITECTURE.md to reflect the current "
                        f"state of the component, then re-run."
                    )
                })
            else:
                passes.append(
                    f"`{component}` — modified, ARCHITECTURE.md updated (pass)"
                )

    return blocks, passes
# ─────────────────────────────────────────────
# TEST REPORT
# ─────────────────────────────────────────────

def build_test_report(
    feature_name,
    test_success,
    test_output,
    failure_count,
    overall_line,
    overall_branch,
    failing_classes,
    excluded_classes
):
    """
    Build the markdown content for TEST_REPORT.md.
    """
    coverage_status = (
        "PASS"
        if overall_line is not None
        and overall_line >= COVERAGE_THRESHOLD
        and overall_branch >= COVERAGE_THRESHOLD
        else "FAIL"
    )
    test_status = "PASS" if test_success else "FAIL"

    lines = [
        f"# Test Report — {feature_name}",
        "",
        "## Summary",
        "",
        f"| Check | Result |",
        f"|-------|--------|",
        f"| Tests | {test_status} |",
        f"| Line Coverage | {overall_line}% ({'PASS' if overall_line and overall_line >= COVERAGE_THRESHOLD else 'FAIL'}) |",
        f"| Branch Coverage | {overall_branch}% ({'PASS' if overall_branch and overall_branch >= COVERAGE_THRESHOLD else 'FAIL'}) |",
        "",
        "## Coverage Results",
        "",
        f"- **Line coverage:** {overall_line}% (threshold: {COVERAGE_THRESHOLD}%)",
        f"- **Branch coverage:** {overall_branch}% (threshold: {COVERAGE_THRESHOLD}%)",
        "",
    ]

    if excluded_classes:
        lines += [
            "### Excluded Classes",
            "_Per TESTING_POLICY.md lines 20-30 — excluded from threshold:_",
            ""
        ]
        for cls in excluded_classes:
            lines.append(f"- {cls}")
        lines.append("")

    if failing_classes:
        lines += [
            "### Classes Below Threshold",
            ""
        ]
        for cls in failing_classes:
            lines.append(
                f"- `{cls['name']}` — "
                f"line: {cls['line_pct']}%, branch: {cls['branch_pct']}%"
            )
        lines.append("")

    lines += [
        "## Test Results",
        "",
        f"- **Status:** {test_status}",
        f"- **Failures/Errors:** {failure_count if failure_count >= 0 else 'unknown'}",
        "",
        "### Maven Output",
        "",
        "```",
        test_output[-3000:] if len(test_output) > 3000 else test_output,
        "```",
        "",
        "## Observations",
        "",
        "_Add any observations about test quality, gaps, or edge cases here._",
    ]

    return "\n".join(lines)

def build_fix_plan(
    feature_name,
    test_success,
    failure_count,
    overall_line,
    overall_branch,
    failing_classes,
    test_output
):
    """
    Build the markdown content for FIX_PLAN.md.
    Per updated rules: FIX_PLAN is informational only.
    No closure gate — if it doesn't work after fix, that's a bug.
    """
    issues = []

    if not test_success:
        issues.append(
            f"**Test failures:** {failure_count} failure(s)/error(s) detected"
        )
    if overall_line is not None and overall_line < COVERAGE_THRESHOLD:
        issues.append(
            f"**Line coverage:** {overall_line}% is below the {COVERAGE_THRESHOLD}% threshold"
        )
    if overall_branch is not None and overall_branch < COVERAGE_THRESHOLD:
        issues.append(
            f"**Branch coverage:** {overall_branch}% is below the {COVERAGE_THRESHOLD}% threshold"
        )
    for cls in failing_classes:
        issues.append(
            f"**Low coverage class:** `{cls['name']}` "
            f"(line: {cls['line_pct']}%, branch: {cls['branch_pct']}%)"
        )

    lines = [
        f"# Fix Plan — {feature_name}",
        "",
        "## Root Cause",
        "",
    ]

    for issue in issues:
        lines.append(f"- {issue}")

    lines += [
        "",
        "## Step-by-Step Fix",
        "",
        "_To be completed by Claude during the fix cycle:_",
        "",
        "1. ",
        "2. ",
        "3. ",
        "",
        "## Fix Applied",
        "",
        "_Update this section after the fix is implemented:_",
        "",
        "- **What was changed:**",
        "- **Why it resolves the issue:**",
        "",
        "## Relevant Test Output",
        "",
        "```",
        test_output[-2000:] if len(test_output) > 2000 else test_output,
        "```",
    ]

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
        repo_root = find_repo_root()
        feature_name = find_active_feature(repo_root)

        if not feature_name:
            # No active feature plan — this might be a non-feature session
            # Don't block, just exit cleanly
            sys.exit(0)

        blocks = []
        warnings = []
        actions_taken = []

        # ── 1. Run mvn test ───────────────────────────────────────────────────
        test_success, test_output, failure_count = run_maven_tests(repo_root)

        # ── 2. Run JaCoCo and parse coverage ─────────────────────────────────
        run_jacoco_report(repo_root)
        overall_line, overall_branch, failing_classes, excluded_classes = \
            parse_jacoco_coverage(repo_root)

        jacoco_report_exists = os.path.exists(
            os.path.join(repo_root, JACOCO_REPORT)
        )

        coverage_ok = (
            jacoco_report_exists
            and overall_line is not None
            and overall_line >= COVERAGE_THRESHOLD
            and overall_branch >= COVERAGE_THRESHOLD
        )

        # ── 3. Architecture.md component compliance ───────────────────────────
        arch_content, arch_path = read_architecture_md(repo_root)
        changed_files = get_git_changed_files(repo_root)
        new_components, modified_components = classify_java_changes(
            changed_files, repo_root
        )

        arch_component_blocks, arch_component_passes = build_architecture_compliance_report(
            new_components,
            modified_components,
            arch_content,
            changed_files,
            repo_root
        )

        # ── 4. Write TEST_REPORT.md ───────────────────────────────────────────
        testing_dir = os.path.join(repo_root, "testing", feature_name)
        os.makedirs(testing_dir, exist_ok=True)

        report_path = os.path.join(testing_dir, "TEST_REPORT.md")
        report_content = build_test_report(
            feature_name, test_success, test_output, failure_count,
            overall_line, overall_branch, failing_classes, excluded_classes
        )
        with open(report_path, "w", encoding="utf-8") as f:
            f.write(report_content)
        actions_taken.append(f"Created testing/{feature_name}/TEST_REPORT.md")

        # ── 5. Write FIX_PLAN.md if needed ───────────────────────────────────
        fix_plan_path = None
        if not test_success or not coverage_ok:
            fix_plan_path = os.path.join(testing_dir, "FIX_PLAN.md")
            fix_content = build_fix_plan(
                feature_name, test_success, failure_count,
                overall_line, overall_branch, failing_classes, test_output
            )
            with open(fix_plan_path, "w", encoding="utf-8") as f:
                f.write(fix_content)
            actions_taken.append(f"Created testing/{feature_name}/FIX_PLAN.md")

        # ── 6. Build block messages ───────────────────────────────────────────
        if not test_success:
            if failure_count == -1:
                blocks.append(
                    "BLOCKED — MAVEN NOT AVAILABLE\n"
                    "  mvn was not found on PATH. Maven must be installed and "
                    "accessible for the definition of done to be verified.\n"
                    "  Ensure Maven is configured correctly and re-run."
                )
            else:
                blocks.append(
                    f"BLOCKED — TEST FAILURES DETECTED\n"
                    f"  {failure_count} test failure(s)/error(s) found.\n"
                    f"  Per ARCHITECTURE.md: mvn test must produce "
                    f"zero failures before this work is considered done.\n"
                    f"  FIX_PLAN.md has been created at "
                    f"testing/{feature_name}/FIX_PLAN.md.\n"
                    f"  Complete the step-by-step fix, update FIX_PLAN.md with "
                    f"how it was applied, then re-run."
                )

        if not coverage_ok:
            if not jacoco_report_exists:
                blocks.append(
                    "BLOCKED — JACOCO REPORT NOT FOUND\n"
                    "  No JaCoCo XML report was found at "
                    f"{JACOCO_REPORT}.\n"
                    "  This means either mvn jacoco:report did not run, "
                    "JaCoCo is not configured in pom.xml, or mvn test "
                    "failed before the report could be generated.\n"
                    "  Coverage cannot be verified without this report. "
                    "Ensure JaCoCo is configured and re-run."
                )

        if not coverage_ok and jacoco_report_exists and overall_line is not None:
            coverage_lines = [
                f"BLOCKED — COVERAGE BELOW THRESHOLD\n"
                f"  Line coverage:   {overall_line}% "
                f"({'PASS' if overall_line >= COVERAGE_THRESHOLD else 'FAIL'})\n"
                f"  Branch coverage: {overall_branch}% "
                f"({'PASS' if overall_branch >= COVERAGE_THRESHOLD else 'FAIL'})\n"
                f"  Required: {COVERAGE_THRESHOLD}% for both "
                f"(per TESTING_POLICY.md, after exclusions).\n"
            ]
            if failing_classes:
                coverage_lines.append("  Classes below threshold:")
                for cls in failing_classes:
                    coverage_lines.append(
                        f"    - {cls['name']} "
                        f"(line: {cls['line_pct']}%, branch: {cls['branch_pct']}%)"
                    )
            coverage_lines.append(
                f"\n  Add tests for the classes listed above. "
                f"FIX_PLAN.md has been created at "
                f"testing/{feature_name}/FIX_PLAN.md."
            )
            blocks.append("\n".join(coverage_lines))

        # Architecture component compliance blocks
        for arch_block in arch_component_blocks:
            blocks.append(arch_block["message"])

        # ── 7. Build the context summary for Claude ───────────────────────────
        summary_lines = [
            "═══════════════════════════════════════════════════",
            "DEFINITION OF DONE — RESULTS",
            "═══════════════════════════════════════════════════",
            "",
            f"Feature: {feature_name}",
            "",
            "Actions taken by this hook:",
        ]
        for action in actions_taken:
            summary_lines.append(f"  ✓ {action}")

        summary_lines += [
            "",
            "───────────────────────────────────────────────────",
            "CHECKS",
            "───────────────────────────────────────────────────",
            "",
            f"  Tests:           {'PASS' if test_success else 'FAIL'} "
            f"({failure_count} failure(s))",
            f"  Line coverage:   "
            f"{overall_line if overall_line is not None else 'N/A'}% "
            f"({'PASS' if overall_line and overall_line >= COVERAGE_THRESHOLD else 'FAIL'})",
            f"  Branch coverage: "
            f"{overall_branch if overall_branch is not None else 'N/A'}% "
            f"({'PASS' if overall_branch and overall_branch >= COVERAGE_THRESHOLD else 'FAIL'})",
            f"  ARCHITECTURE.md components:",
        ]

        if arch_component_passes:
            for p in arch_component_passes:
                summary_lines.append(f"    ✓ {p}")

        if arch_component_blocks:
            for b in arch_component_blocks:
                summary_lines.append(
                    f"    ✗ {b['component']} — "
                    f"{'not documented' if b['reason'] in ('new_not_documented', 'modified_never_documented') else 'stale documentation'}"
                )
        elif not arch_component_passes:
            summary_lines.append("    — no Java component changes detected")

        summary_lines.append("")

        if blocks:
            summary_lines += [
                "───────────────────────────────────────────────────",
                "BLOCKED — DO NOT MARK DONE",
                "───────────────────────────────────────────────────",
                "",
            ]
            for i, block in enumerate(blocks, 1):
                summary_lines.append(f"Issue {i}:")
                summary_lines.append(block)
                summary_lines.append("")
            summary_lines.append(
                "Resolve all issues above before this work is considered done."
            )
        else:
            summary_lines += [
                "───────────────────────────────────────────────────",
                "ALL CHECKS PASSED",
                "───────────────────────────────────────────────────",
                "",
                "This work has met the definition of done.",
                "TEST_REPORT.md has been written to "
                f"testing/{feature_name}/TEST_REPORT.md.",
                "You may proceed to PR creation.",
            ]

        summary_lines.append("═══════════════════════════════════════════════════")

        final_context = "\n".join(summary_lines)

        # Hard block via exit 2 if any blockers exist
        if blocks:
            print(final_context, file=sys.stderr)
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