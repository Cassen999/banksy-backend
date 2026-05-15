# Testing Policy and Enforcement Rules

## Overview
You are responsible for enforcing a strict, production-quality testing strategy for a Java Spring Boot backend application. This project is a portfolio-level system and must reflect industry best practices.

All code must be tested. No exceptions.

---

## Core Requirements

### Test Coverage
- Minimum **90% global test coverage**
- Coverage must include:
    - **Line coverage**
    - **Branch coverage**
- Coverage is enforced via **JaCoCo**
- The build must **FAIL** if coverage drops below 90%

---

## Testing Scope

### Required Test Types

#### 1. Unit Tests (Primary)
- Cover:
    - Service layer (business logic)
    - Utility/helper classes
- Use mocking for dependencies

#### 2. Integration Tests
- Cover:
    - Controller layer (API endpoints)
    - Repository layer (database interaction)

#### Explicitly Excluded
- End-to-end (E2E) tests are NOT required

---

## Testing Stack (MANDATORY)

All tests must use the following tools:

- JUnit 5
- Mockito
- Spring Boot Test
- MockMvc (for controller/API testing)
- Testcontainers (for database testing)
- AssertJ (for assertions)
- JaCoCo (for coverage reporting)

Do not introduce alternative libraries unless explicitly approved.

---

## Database Testing Strategy

- Integration tests MUST use **Testcontainers**
- The container must match the production database type
- Do NOT use in-memory databases like H2

---

## Test Design Standards

### Structure
- Follow **AAA pattern**:
    - Arrange
    - Act
    - Assert

### Naming Convention
Test method names must follow this format:
should<ExpectedBehavior>_when<Condition>

Examples:
- `shouldCreateAccount_whenValidInputProvided`
- `shouldThrowException_whenBalanceIsNegative`

### Assertions
- Multiple assertions are allowed if they validate a single logical behavior
- Use **AssertJ** for all assertions

### Organization
- One test class per class under test
- Mirror package structure of source code

---

## Test Distribution Strategy

Maintain a balanced test pyramid:

- ~70% Unit Tests (fast, isolated)
- ~30% Integration Tests (realistic, full stack components)

---

## AI Agent Workflow Requirements

### BEFORE Writing Code
You MUST generate:

1. `IMPLEMENTATION_PLAN.md`
    - Describe how the feature will be built

2. `TEST_PLAN.md`
    - List:
        - Test cases
        - Edge cases
        - Failure scenarios

You may NOT proceed to implementation until both are complete.

---

### DURING Implementation

- All new code MUST include corresponding tests
- Tests must be written alongside or before implementation
- You are NOT allowed to leave code untested

---

### AFTER Implementation

You MUST generate:

1. `TEST_REPORT.md`
    - Coverage results
    - Passed/failed tests
    - Observations

2. `FIX_PLAN.md` (if needed)
    - Required if:
        - Any test fails
        - Coverage < 90%
    - Must include:
        - Root cause
        - Step-by-step fix plan

---

## CI/CD Enforcement

- Tests must run on **every pull request**
- Coverage must be validated in CI
- The build MUST fail if:
    - Coverage < 90%
    - Any test fails

---

## Strict Enforcement Rules

You MUST:

- Refuse to generate code without tests
- Refuse to finalize any feature that does not meet coverage requirements
- Refuse to proceed if:
    - `TEST_PLAN.md` is missing
    - Tests are incomplete
    - Coverage is below 90%

---

## Quality Expectations

- Tests must be readable and maintainable
- Avoid redundant or meaningless tests
- Focus on behavior, not implementation details
- Ensure edge cases are covered

---

## Summary

This project must demonstrate:

- Strong backend engineering practices
- Real-world testing strategies
- High code quality and reliability

Testing is NOT optional. It is a first-class requirement.

# Testing Policy Persistence Instruction

## Objective
You must ensure that the project's testing policy is both understood and persisted within the repository.

---

## Requirements

### 1. Policy Understanding
- You MUST fully adopt and follow the provided testing policy
- All future implementation, planning, and code generation must comply with this policy
- The testing policy is considered a **non-optional system constraint**

---

### 2. Policy File Creation

You MUST create the following file in the repository:
plans/Testing/TESTING_POLICY.md

### File Requirements

- The file MUST contain the **complete testing policy exactly as provided**
- Do NOT summarize, shorten, or reinterpret the policy
- Preserve:
    - All sections
    - All rules
    - All formatting
- The content must be written in clean Markdown format

---

### 3. Directory Handling

- If the directory structure does not exist:
    - `plans/`
    - `plans/Testing/`

You MUST create it before writing the file.

---

### 4. Execution Order

You MUST perform the following steps in order:

1. Validate that the testing policy has been received
2. Create directory structure if missing
3. Write `TESTING_POLICY.md` with full policy contents
4. Confirm the file has been successfully created

---

### 5. Enforcement Rule

You MUST NOT proceed with:
- Feature implementation
- Code generation
- Test creation

UNTIL the `TESTING_POLICY.md` file exists and is correctly populated.

---

## Strict Constraints

- Do NOT modify the policy content
- Do NOT delay file creation
- Do NOT proceed without persistence

---

## Summary

The testing policy must exist as a **single source of truth** inside:
plans/Testing/TESTING_POLICY.md

This file is mandatory and must be created before any development work begins.