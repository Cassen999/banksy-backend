# Testing Policy and Enforcement Rules

## Overview
You are responsible for enforcing a strict, production-quality testing strategy for a Java Spring Boot backend application. This project is a portfolio-level system and must reflect industry best practices.

All code must be tested. No exceptions.

---

## Core Requirements

### Test Coverage
- Minimum **90% coverage** applied to all testable code
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
- All test files live under `src/test/java/` — never co-located with source files
- Mirror the package structure of `src/main/java/` exactly
  - Example: `src/main/java/org/example/service/AccountService.java` → `src/test/java/org/example/service/AccountServiceTest.java`

---

## Test Distribution Strategy

Maintain a balanced test pyramid:

- ~70% Unit Tests (fast, isolated)
- ~30% Integration Tests (realistic, full stack components)

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
