# Implementation Plan: Baseline Test Coverage

## Goal
Establish 90% line and branch coverage across all testable classes in the existing codebase.
This is not a new feature — it is the initial test baseline for code already written.

---

## Classes in Scope

### Excluded from JaCoCo (per TESTING_POLICY.md + additions below)
| Class / Pattern | Reason |
|---|---|
| `Main.java` | Spring Boot entry point |
| `config/*` | Spring wiring, no business logic |
| `entity/*` | JPA POJOs |
| `plaid/GenerateAccessToken.java` | Sandbox dev utility, not production code |
| `plaid/PlaidClientFactory.java` | Pure infrastructure factory; delegates to config env vars |

### Testable classes (12)
| Class | Test Type |
|---|---|
| `EncryptionService` | Unit |
| `CustomOAuth2UserService` | Unit |
| `PlaidLinkService` | Unit |
| `BalanceService` | Unit |
| `TransactionsService` | Unit |
| `SecurityUtils` | Unit |
| `AuthController` | Integration (MockMvc) |
| `PlaidLinkController` | Integration (MockMvc) |
| `BalanceController` | Integration (MockMvc) |
| `TransactionsController` | Integration (MockMvc) |
| `UserRepository` + `OAuthIdentityRepository` + `PlaidItemRepository` + `PlaidAccountRepository` | Integration (Testcontainers) |

---

## Production Code Changes Required for Testability

Two minimal, non-behavioural changes are needed before tests can be written.

### 1. `EncryptionService` — package-private test constructor
The no-arg `@Service` constructor reads `ENCRYPTION_KEY` from Dotenv/env at startup.
A package-private constructor accepting a `SecretKey` directly is added so unit tests can
instantiate the service with a known key without touching the environment.
The existing no-arg constructor is unchanged.

### 2. `CustomOAuth2UserService` — extract super call to protected method
`loadUser()` calls `super.loadUser(userRequest)` (Spring's `OidcUserService`), which makes
outbound HTTP calls to Google. Extracting this single line to a `protected loadFromOidcProvider()`
method allows tests to spy on the class and stub only that dependency, leaving all user
registration business logic exercisable.

---

## Test Infrastructure

### JaCoCo exclusions (pom.xml)
Add `GenerateAccessToken` and `PlaidClientFactory` to the existing exclusion list.

### Maven Surefire env vars (pom.xml)
Add `<environmentVariables>` so that any test loading the full Spring context can satisfy
`EncryptionService`'s env-var requirement without a `.env` file:
- `ENCRYPTION_KEY` — 32-byte test key (all-zeros, base64-encoded; safe for tests only)
- `PLAID_CLIENT_ID`, `PLAID_SECRET`, `PLAID_ENVIRONMENT` — dummy values for Plaid config

### `src/test/resources/application-test.properties`
Spring profile `test` overrides:
- OAuth2 client-id / client-secret → dummy values (satisfies Spring Security auto-config)
- Datasource URL left blank — `@DynamicPropertySource` in repository tests overrides it at runtime

### `AbstractRepositoryTest`
Base class shared by all `@DataJpaTest` tests. Declares a single static Testcontainers
PostgreSQL container. Flyway migrations run automatically against it, matching production schema.

---

## Test Distribution
- Unit tests: ~70% (services + utilities)
- Controller integration tests: ~20% (MockMvc, no database)
- Repository integration tests: ~10% (Testcontainers PostgreSQL)
