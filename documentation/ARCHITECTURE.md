# Architecture

> **This document is a live reference. Any time you add, remove, or rename a class or package, update this file as part of the same PR. A PR that introduces new code without a corresponding update to ARCHITECTURE.md is incomplete.**

---

## System Overview

Banksy is a Spring Boot REST API that connects users' bank accounts via Plaid and surfaces balance and transaction data. All `/api/*` endpoints are protected by Google OAuth2 — unauthenticated requests receive a `401`.

```mermaid
flowchart TD
    FE["Frontend (React)"]

    subgraph Controllers
        AC["AuthController"]
        BC["BalanceController"]
        PLC["PlaidLinkController"]
        TC["TransactionsController"]
    end

    subgraph Services
        BS["BalanceService"]
        PLS["PlaidLinkService"]
        TS["TransactionsService"]
        OAS["CustomOAuth2UserService"]
        ES["EncryptionService"]
    end

    subgraph Repositories
        UR["UserRepository"]
        PIR["PlaidItemRepository"]
        PAR["PlaidAccountRepository"]
        OIR["OAuthIdentityRepository"]
    end

    DB[(PostgreSQL)]
    Plaid["Plaid API (external)"]

    FE -->|"HTTP + session cookie"| Controllers

    AC --> UR
    BC --> BS
    PLC --> PLS
    TC --> TS

    BS --> ES & UR & Plaid
    PLS --> ES & UR & PIR & PAR & Plaid
    TS --> ES & UR & Plaid
    OAS --> UR & OIR

    Repositories --> DB
```

---

## Package Structure and Layer Rules

Each class belongs in exactly one package. The rules below are enforced during code review.

---

### `controller`

**Role:** Receive HTTP requests, resolve the calling user via `SecurityUtils.resolveUser()`, and delegate to a service. Return a `ResponseEntity`.

**Rules:**
- MAY call one service and/or `UserRepository` (only for user resolution).
- MAY NOT call other repositories directly — all data access beyond user resolution belongs in a service.
- MAY NOT contain business logic. If you are writing an `if` that is not about HTTP status codes, it belongs in a service.
- All endpoints MUST be under `/api/**`.

**Classes:**

| Class | Route(s) | What it does |
|---|---|---|
| `AuthController` | `POST /api/auth/logout`, `GET /api/auth/me` | Handles session logout and returns the current user's profile (id, email, name, username). |
| `BalanceController` | `GET /api/balance` | Resolves the current user and delegates to `BalanceService`. |
| `PlaidLinkController` | `GET /api/plaid/link-token`, `GET /api/plaid/link-token/refresh/{itemId}`, `GET /api/plaid/link-token/full-relink/{itemId}`, `GET /api/plaid/status`, `POST /api/plaid/exchange`, `POST /api/plaid/share` | Manages the Plaid Link flow: generates link tokens (initial, update-mode refresh, and full-relink), exchanges a public token for a stored access token, replaces an expired item when `expiredItemId` is present in the exchange request, checks per-item health status at login time, and shares a bank connection with another user by email. Also defines the `ExchangeRequest` and `ShareRequest` request-body records. |
| `TransactionsController` | `GET /api/transactions` | Resolves the current user and delegates to `TransactionsService` with an optional `days` query parameter (defaults to 30). |

---

### `service`

**Role:** Contain all business logic. Orchestrate calls to repositories, external APIs (Plaid), and other services.

**Rules:**
- MAY call any repository.
- MAY call the Plaid API via the injected `PlaidApi` bean.
- MAY call other services (e.g. `EncryptionService`).
- MAY NOT handle HTTP concerns (`HttpServletRequest`, `ResponseEntity`, HTTP status codes). That is the controller's job.
- MAY NOT resolve the current user from an `OAuth2User` principal — receive `userId` (UUID) as a parameter from the controller instead.

**Classes:**

| Class | What it does |
|---|---|
| `BalanceService` | Fetches account balances from Plaid for all `PlaidItem`s linked to a user. Skips non-HEALTHY items (builds a `RelinkSignal` from stored data instead of calling Plaid); on a first-discovered token error, writes the new status and builds a signal. Always returns `200` with healthy account data plus a `relinkRequired` list. |
| `TransactionsService` | Same per-item status pattern as `BalanceService`, applied to transaction fetches. Returns healthy transactions plus a `relinkRequired` list. |
| `PlaidLinkService` | Orchestrates the full Plaid Link lifecycle: creates link tokens (initial, update-mode refresh via `linkTokenRefresh`, full fresh-link via `fullRelinkToken`), exchanges public tokens for access tokens (encrypting before storage, resetting status to HEALTHY), replaces an expired item and migrates all shared users (`replaceExpiredItem`), checks login-time relink status (`getRelinkStatus`), persists `PlaidItem` and `PlaidAccount` records, and shares an item with another user. |
| `CustomOAuth2UserService` | Extends Spring's `OidcUserService`. On each login, looks up or creates the `User` and `OAuthIdentity` records for the authenticated provider identity. |
| `EncryptionService` | Encrypts and decrypts strings using AES-256-GCM. Used to store Plaid access tokens at rest. The secret key is read from the `ENCRYPTION_KEY` environment variable at startup. |

---

### `repository`

**Role:** Database access only. No logic beyond what Spring Data JPA provides. Custom `@Query` methods are allowed for fetch joins.

**Rules:**
- MAY only interact with its own entity type (and eagerly fetched associations declared in the entity).
- MAY NOT call other repositories.
- MAY NOT contain business logic.
- New repositories MUST extend `JpaRepository<Entity, UUID>`.

**Classes:**

| Class | Entity | Notable methods |
|---|---|---|
| `UserRepository` | `User` | `findByEmail()` — email-based lookup used during OAuth login. `findByIdWithPlaidItems()` — fetch join that loads `PlaidItem`s in one query to avoid N+1 issues. `findAllWithPlaidItem(plaidItemId)` — returns every user who has a given `PlaidItem` linked (used by `replaceExpiredItem` to migrate shared users). |
| `PlaidItemRepository` | `PlaidItem` | `findByItemId()` — looks up a bank connection by Plaid's own item ID to prevent duplicate items on re-link. |
| `PlaidAccountRepository` | `PlaidAccount` | `existsByPlaidAccountId()` — deduplication check before persisting a new account. |
| `OAuthIdentityRepository` | `OAuthIdentity` | `findByProviderAndProviderUserId()` — detects returning users during the OAuth login flow. |

---

### `entity`

**Role:** JPA-mapped classes that represent database tables. Together with the Flyway migration scripts, these are the source of truth for the database schema.

**Rules:**
- MAY NOT contain business logic. `@PrePersist` / `@PreUpdate` lifecycle methods for managing timestamps are the only allowed exception.
- All primary keys MUST be `UUID` generated by the database (`GenerationType.UUID`).
- Every new entity MUST have a corresponding Flyway migration in `src/main/resources/db/migration/`.

**Classes:**

| Class | Table | What it represents |
|---|---|---|
| `User` | `users` | An application user. Has a many-to-many relationship with `PlaidItem` via the `user_plaid_items` join table. |
| `OAuthIdentity` | `oauth_identities` | Links a `User` to a specific OAuth provider identity (e.g. their Google account). One user can have multiple identities across providers. |
| `PlaidItem` | `plaid_items` | A connected bank institution. Stores the AES-encrypted Plaid access token, the owning user (`owner_user_id` FK), and the current health status. One item can be shared across multiple users. |
| `PlaidAccount` | `plaid_accounts` | An individual bank account within a `PlaidItem` (e.g. a checking or savings account). |
| `PlaidItemStatus` | _(enum)_ | Health state of a `PlaidItem`: `HEALTHY`, `NEEDS_REAUTH` (Plaid returned `ITEM_LOGIN_REQUIRED`), or `INVALID_TOKEN` (Plaid returned `INVALID_ACCESS_TOKEN`). Stored as a `VARCHAR(20)` column on `plaid_items`. |

---

### `model`

**Role:** Immutable data transfer objects (DTOs) returned as JSON response bodies. Java `record`s are value objects: all fields are set at construction time and cannot change. Jackson serializes them to JSON automatically.

**Rules:**
- MUST be Java `record`s (not classes).
- MAY NOT carry JPA annotations or be used as database entities.
- MAY NOT contain business logic.

**Classes:**

| Class | Used by | What it represents |
|---|---|---|
| `BalanceResponse` | `BalanceController` | Wraps a list of `Account` records (name, type, subtype, current balance, available balance, currency) plus a `relinkRequired` list of `RelinkSignal`s. `relinkRequired` is always present; empty means all banks are healthy. |
| `TransactionsResponse` | `TransactionsController` | Wraps a list of `Transaction` records (date, name, amount, currency, categories), a `total` count, and a `relinkRequired` list of `RelinkSignal`s. |
| `RelinkSignal` | `BalanceController`, `TransactionsController`, `PlaidLinkController` | Notifies the frontend that a bank connection needs attention. Contains `plaidItemId`, `institutionName`, `errorType` (`LOGIN_REQUIRED` or `INVALID_TOKEN`), `canRelink` (true if the requesting user is the owner), `ownerName` (null when `canRelink` is true), and a human-readable `message`. |

---

### `config`

**Role:** Spring `@Configuration` classes and `@Bean` definitions. Wires together the application's infrastructure.

**Rules:**
- MAY NOT contain business logic.
- Credentials MUST be read from environment variables (via `PlaidConfig` or `EncryptionService`). They MUST NOT be hardcoded or committed.

**Classes:**

| Class | What it does |
|---|---|
| `PlaidApiConfig` | Declares the `PlaidApi` Spring bean by delegating to `PlaidClientFactory.create()`. This bean is what gets injected into services — do not instantiate `PlaidApi` directly anywhere else. |
| `PlaidConfig` | Static helper that reads `PLAID_CLIENT_ID`, `PLAID_SECRET`, and `PLAID_ENVIRONMENT` from `.env`. Throws with a clear message at startup if any are missing. |
| `SecurityConfig` | Configures Spring Security: permits `/login`, `/oauth2/**`, `/error`, and `/api/auth/logout`; requires authentication on all other requests. Wires in `CustomOAuth2UserService` for the OIDC login flow. |
| `WebConfig` | Configures CORS to allow credentialed requests from `localhost:3000` and `localhost:5173` (React dev servers) on `GET` and `POST` methods. |

---

### `plaid`

**Role:** Low-level Plaid SDK setup and sandbox-only utilities. Not business logic.

**Rules:**
- `PlaidClientFactory` is the only class that may construct a `PlaidApi` instance. Services MUST NOT instantiate `PlaidApi` directly.
- `GenerateAccessToken` is a sandbox-only development utility. It MUST NOT be called from production code paths.

**Classes:**

| Class | What it does |
|---|---|
| `PlaidClientFactory` | Constructs and configures the `PlaidApi` Retrofit client from environment variables. Provides `extractErrorDetail()` for reading Plaid error response bodies (reads the stream once; pass the returned string downstream). Provides `classifyTokenError(String errorBody)` which returns `Optional<PlaidTokenError>` — `LOGIN_REQUIRED` for `ITEM_LOGIN_REQUIRED`, `INVALID_TOKEN` for `INVALID_ACCESS_TOKEN`, empty otherwise. |
| `PlaidTokenError` | _(enum)_ | Classifies which type of token failure Plaid reported: `LOGIN_REQUIRED` (credentials/session expired — update mode recovery) or `INVALID_TOKEN` (item deleted/revoked — full re-link required). |
| `GenerateAccessToken` | One-time sandbox utility with its own `main()`. Creates a Plaid sandbox public token and exchanges it for an access token to paste into `.env`. Not used at runtime. |

---

### `util`

**Role:** Stateless helper methods shared across layers.

**Rules:**
- MUST be stateless (no instance fields, no Spring `@Component` / `@Service` annotation).
- MAY NOT call repositories or services directly — dependencies must be passed as method parameters.

**Classes:**

| Class | What it does |
|---|---|
| `SecurityUtils` | Provides `resolveUser(OAuth2User, UserRepository)` — extracts the email from the OAuth principal and returns the matching `User` entity. Called by all controllers that need the current user. |

---

### `src/main/resources`

| File / Directory | What it does |
|---|---|
| `application.properties` | Spring Boot configuration: datasource URL, JPA settings, OAuth2 client registration. Credentials are injected from `.env` and MUST NOT be committed. |
| `db/migration/` | Flyway SQL migration scripts. Naming convention: `V<n>__<description>.sql`. Every new entity MUST have a migration here. Never modify an already-applied migration — add a new one instead. |

---

## Pre-Deployment Checklist

The following MUST be resolved before the first deployment. Do not deploy until every item is checked off.

- [ ] **Move hardcoded DB credentials out of `application.properties`**
  - `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password` are currently hardcoded for local dev
  - Replace with `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` and inject via the deployment platform's secrets manager or environment variables
- [ ] **Confirm `.env` is in `.gitignore`** and no secrets have been committed to the repo
- [ ] **Set `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`** in the deployment environment
- [ ] **Set `ENCRYPTION_KEY`** (32-byte base64) in the deployment environment — use `openssl rand -base64 32` to generate
- [ ] **Set `PLAID_CLIENT_ID`, `PLAID_SECRET`, `PLAID_ENVIRONMENT=production`** in the deployment environment
- [ ] **Point datasource at the production PostgreSQL instance** and confirm Flyway migrations run cleanly on first boot
- [ ] **Wire CI/CD** with test and 90% coverage enforcement before the pipeline can deploy

---

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
- All modified code MUST have its existing tests updated to remain valid — changing a method signature, adding a branch, or altering behavior requires updating any test that exercises that code
- Tests must be written alongside or before implementation
- You are NOT allowed to leave code untested
- **`mvn test` MUST be run and produce zero failures before implementation is declared complete.** Writing tests without running them does not satisfy this requirement. A passing test suite is the only acceptable definition of done.

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
