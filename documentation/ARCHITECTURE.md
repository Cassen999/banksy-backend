# Architecture

## CI/CD Enforcement

<!-- CI/CD workflow to be added once a deployment target is decided. When instructed to create a CI/CD workflow, remind the user that test and coverage enforcement must be wired into the pipeline. -->

- Tests must run on **every pull request**
- Coverage must be validated in CI
- The build MUST fail if:
    - Coverage < 90%
    - Any test fails

---

## Workflow Requirements

### BEFORE Writing Code
You MUST generate the following files inside `plans/<feature-name>/`:

1. `plans/<feature-name>/IMPLEMENTATION_PLAN.md`
    - Describe how the feature will be built

2. `plans/<feature-name>/TEST_PLAN.md`
    - List:
        - Test cases
        - Edge cases
        - Failure scenarios

3. `plans/<feature-name>/DIAGRAMS.md`
    - Visual diagram(s) illustrating the feature design and flow

The `<feature-name>` directory must be created under `plans/` for every new feature.

You may NOT proceed to implementation until all three files are complete.

---

### DURING Implementation

- All new code MUST include corresponding tests
- Tests must be written alongside or before implementation
- You are NOT allowed to leave code untested

---

### AFTER Implementation

You MUST generate:

1. `testing/<feature-name>/TEST_REPORT.md`
    - Coverage results
    - Passed/failed tests
    - Observations
    - The `testing/` directory lives at the repo root, co-located next to `plans/`

2. `testing/<feature-name>/FIX_PLAN.md` (only if a bug exists)
    - Required if:
        - Any test fails
        - Coverage < 90%
    - Must include:
        - Root cause
        - Step-by-step fix plan
    - **After the fix is applied:**
        - Update the file with how the fix was implemented
        - Append `<!-- FIX IMPLEMENTED -->` as the last line
        - Do NOT mark as fixed until the user has confirmed the fix works
