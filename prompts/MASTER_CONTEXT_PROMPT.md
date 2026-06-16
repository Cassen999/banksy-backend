# Master Context Generation Prompt

Paste this prompt at the start of a new Claude Code session (or any Claude session)
when you want to regenerate or update CLAUDE.md.

Replace the placeholder sections marked with <!-- --> before running.

---

## THE PROMPT

```
You are a senior backend engineer working on Banksy, a Spring Boot 3 REST API
that connects users' bank accounts via Plaid and surfaces balance and transaction
data. You are about to generate a CLAUDE.md master context file for this project.

Read the following documents in order before generating anything:
  - ARCHITECTURE.md    — layer rules, component tables, Plaid integration pattern
  - SCHEMA.md          — all database tables, columns, constraints, relationships
  - ENDPOINTS.md       — every implemented API route with request/response shapes

After reading, generate a CLAUDE.md file using the structure below. Every section
is mandatory. Do not collapse, skip, or summarize any section.

This file will be pasted as a system prompt at the start of every Claude Code
session for this project. Write for machine ingestion, not human reading. Be terse
and specific. Avoid prose where a list or table communicates the same thing.

---

## SECTION 1 — PROJECT IDENTITY (locked)

State the following as locked facts, not preferences:
- Runtime: Java 21, Spring Boot 3, Maven
- Group: org.example, artifact: banksy
- Database: PostgreSQL via Spring Data JPA, Flyway for migrations
- Auth: Google OAuth2, session cookie (JSESSIONID)
- Financial data: Plaid API (sandbox and production, runtime-switchable)
- Access token encryption: AES-256-GCM via EncryptionService
- Package root: org.example

---

## SECTION 2 — BUILD COMMANDS (locked)

Include the following commands exactly as written:

  mvn compile                          — compile
  mvn test                             — run all tests (also triggers JaCoCo)
  mvn test -Dtest=ClassName            — run a single test class
  mvn jacoco:report                    — generate coverage report
  mvn package                          — produce target/banksy-1.0-SNAPSHOT.jar
  mvn clean                            — clean build artifacts

---

## SECTION 3 — LAYER RULES (locked, hard constraints)

Generate this section directly from ARCHITECTURE.md. For each layer (Controller,
Service, Repository, Entity, Model), produce a tight bullet list of:
  - What it MAY do
  - What it MUST NOT do
  - Any named patterns that are mandatory (e.g. SecurityUtils.resolveUser,
    PlaidEnvironmentService.getClient)

Mark every rule as a hard constraint. Do not soften with words like "prefer"
or "try to". These are enforced at code review and by hooks.

---

## SECTION 4 — EXISTING COMPONENTS (locked)

Generate three tables from ARCHITECTURE.md:

  Table A: Controllers — class name | route prefix | one-line responsibility
  Table B: Services   — class name | one-line responsibility
  Table C: Repositories — interface name | entity | any notable custom methods

A new context reading this table must be able to check for overlap before
creating any new class. Include every class currently in the codebase.

---

## SECTION 5 — DATABASE QUICK REFERENCE (locked)

Generate a compact table of all current tables from SCHEMA.md:

  table name | primary key type | notable columns | foreign key relationships

Do not reproduce the full SCHEMA.md column detail here — that lives in SCHEMA.md.
The purpose of this section is overlap detection: a model should be able to scan
this table and know whether new data has an existing home before proposing a
migration.

---

## SECTION 6 — WHAT NOT TO DO (locked)

This section contains rejected patterns. A new context must treat every item
here as a closed decision. Do not re-evaluate these.

Include the following items exactly:

  ❌ Do not inject PlaidApi directly — always call PlaidEnvironmentService.getClient()
  ❌ Do not resolve the authenticated user inside a service — controllers call
     SecurityUtils.resolveUser() and pass userId (UUID) to services
  ❌ Do not call repositories directly from controllers (except UserRepository
     for user resolution)
  ❌ Do not put business logic in controllers — if you are writing an if statement
     that is not about an HTTP status code, it belongs in a service
  ❌ Do not add JPA annotations to model records — models are DTOs, not entities
  ❌ Do not hardcode credentials — all secrets come from environment variables
  ❌ Do not use React Context for server state on the frontend (tried, rolled back)
  ❌ Do not modify an already-applied Flyway migration — add a new one instead

---

## SECTION 7 — CURRENT STATE

Generate this section from the information provided below.

### Implemented (complete and tested)
List every controller and its routes as implemented. Pull from ENDPOINTS.md.
State this as fact: these endpoints exist and are working.

### Known issues
None

### Planned / in progress
TBD

---

## SECTION 8 — LIVING DOCUMENTS

State these as the authoritative references for a new context to consult:

  ARCHITECTURE.md  — layer rules and component registry. Read before adding
                     any new class, service, or repository.
  SCHEMA.md        — full table definitions, column types, constraints.
                     Read before writing any Flyway migration or JPA entity.
  ENDPOINTS.md     — all implemented routes with request/response shapes.
                     Read before adding or modifying any endpoint.

These documents are enforced as living docs by definition_of_done.py. Any
session that adds a new endpoint, table, or component must update the
relevant document before the feature is considered done.

---

## SECTION 9 — HOOK SYSTEM (read-only reference)

This project uses three Claude Code hooks. State their behavior as facts so a
new context understands why it will be blocked in certain situations:

  audit_before_code.py (PreToolUse / Bash)
    Fires when any code-related keyword is detected in a prompt.
    Injects ARCHITECTURE.md and current schema from Flyway migrations.
    Hard blocks if plans/.active-feature is missing or points to a
    folder without all three plan documents.

  enforce_during_implementation.py (PreToolUse / file writes)
    Fires on every file write to src/main/java/.
    Hard blocks if the corresponding test file does not exist.
    Hard blocks if plans are incomplete or user approval is absent.
    Warns when modifying an existing file without touching its tests.

  definition_of_done.py (PostToolUse / Bash)
    Fires after implementation work.
    Runs mvn test and mvn jacoco:report.
    Hard blocks if: any test fails, line coverage < 90%, branch coverage < 90%,
    new/modified components are not documented in ARCHITECTURE.md,
    or ENDPOINTS.md / SCHEMA.md are stale relative to changed files.
    Writes TEST_REPORT.md and FIX_PLAN.md to testing/<feature-name>/.

  Coverage exclusions (not counted toward 90%):
    Main, *Config, *Configuration, *Exception, entity/* package

---

## SECTION 10 — ACTIVE FEATURE TRACKING

State the following as operational rules:

  plans/.active-feature must always contain the exact name of the feature
  currently being worked on. This name must match an existing folder under plans/.

  When starting a new feature:
    1. Choose a kebab-case feature name (e.g. budget-tracking)
    2. Write that name to plans/.active-feature
    3. Create plans/<feature-name>/IMPLEMENTATION_PLAN.md
    4. Create plans/<feature-name>/TEST_PLAN.md
    5. Create plans/<feature-name>/DIAGRAMS.md
    6. Present all three to the user and wait for explicit approval
    7. Do not write any implementation code until approval is received

  Approval keywords the user may use:
    approved, approve, lgtm, looks good, go ahead, proceed, ship it,
    good to go, confirmed, confirm, happy with, commence,
    start implementation, begin implementation, start coding, begin coding

---

Output the complete CLAUDE.md file now. Do not include any preamble, explanation,
or commentary before or after the file content. The output should be the file
itself and nothing else.
```
