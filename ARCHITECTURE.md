# Architecture

> **Audience:** Developers adding features to Banksy.

Banksy is a Spring Boot 3 application with a PostgreSQL database, Google OAuth2 authentication, and Plaid as its financial data provider.

---

## Layer overview

```
HTTP request
    │
    ▼
Controller      — Maps routes, resolves the authenticated user, delegates to a service,
                  maps exceptions to HTTP status codes.
    │
    ▼
Service         — Business logic. Calls Plaid, reads/writes via repositories, builds
                  response DTOs. @Transactional where DB mutations occur.
    │
    ▼
Repository      — Spring Data JPA interfaces. Custom @Query annotations for joins and
                  filtered lookups.
    │
    ▼
Entity          — JPA-managed tables. Relationships are LAZY by default; eager-load
                  via JOIN FETCH in repository queries only where needed.
```

---

## Components

### Controllers (`org.example.controller`)

| Class | Route prefix | Responsibility |
|-------|--------------|----------------|
| `AuthController` | `/api/auth` | User profile |
| `BalanceController` | `/api/balance` | Live account balances |
| `TransactionsController` | `/api/transactions` | Transaction history |
| `RecurringController` | `/api/recurring` | Recurring transaction streams |
| `PlaidLinkController` | `/api/plaid` | Link token creation, public token exchange, item sharing |
| `RemoveBankController` | `/api/plaid` | Hide accounts, remove items |
| `PlaidAdminController` | `/api/dev/plaid` | Dev-only environment toggle |

### Services (`org.example.service`)

| Class | Responsibility |
|-------|----------------|
| `BalanceService` | Fetches live balances from Plaid for all or one Item |
| `TransactionsService` | Fetches transaction history from Plaid |
| `RecurringService` | Fetches recurring stream data from Plaid (get-all and per-account modes) |
| `PlaidLinkService` | Creates link tokens, exchanges public tokens, manages Item sharing |
| `RemoveBankService` | Hides accounts and removes Items (revokes Plaid access token) |
| `PlaidEnvironmentService` | Runtime-switchable Plaid client (sandbox ↔ production) |
| `EncryptionService` | AES encryption/decryption of stored Plaid access tokens |
| `NotificationService` | In-app notifications sent to linked users on bank events |
| `CustomOAuth2UserService` | Upserts users on Google OAuth2 login |

### Models (`org.example.model`)

Response DTOs returned by controllers. All are Java records.

| Class | Used by | Description |
|-------|---------|-------------|
| `BalanceResponse` | `BalanceController` | Account balances and relink signals |
| `TransactionsResponse` | `TransactionsController` | Transaction list and relink signals |
| `RecurringResponse` | `RecurringController` | Inflow/outflow recurring stream lists and relink signals. Contains nested records `TransactionStreamDto`, `AmountDto`, and `PersonalFinanceCategoryDto` |
| `RelinkSignal` | All data endpoints | Signals a non-HEALTHY bank Item that needs user action |

### Repositories (`org.example.repository`)

| Interface | Entity |
|-----------|--------|
| `UserRepository` | `User` — with `findByIdWithPlaidItems` (JOIN FETCH) and `findAllWithPlaidItem` |
| `PlaidItemRepository` | `PlaidItem` |
| `PlaidAccountRepository` | `PlaidAccount` — with `findByIdWithItem`, `findByPlaidAccountIdWithItem`, `findHiddenAccountIdsByItemId` |
| `PlaidEnvironmentConfigRepository` | `PlaidEnvironmentConfig` |
| `NotificationRepository` | `Notification` |
| `OAuthIdentityRepository` | `OAuthIdentity` |

### Entities (`org.example.entity`)

| Class | Table | Notes |
|-------|-------|-------|
| `User` | `users` | Has a many-to-many to `PlaidItem` via `user_plaid_items` |
| `PlaidItem` | `plaid_items` | One Plaid Item per bank connection; holds encrypted access token; has an `owner` (User) |
| `PlaidAccount` | `plaid_accounts` | One row per account within an Item; `hidden` flag for soft-removal |
| `PlaidItemStatus` | — | Enum: `HEALTHY`, `NEEDS_REAUTH`, `INVALID_TOKEN` |
| `PlaidEnvironmentConfig` | `plaid_environment_config` | Persists active Plaid environment across restarts |
| `Notification` | `notifications` | In-app notification record |
| `OAuthIdentity` | `oauth_identities` | Links a Google sub to a User |

---

## Plaid integration patterns

All Plaid-consuming services follow this pattern:

1. Load the user's linked Items via `userRepository.findByIdWithPlaidItems(userId)`.
2. For each Item: skip if `status != HEALTHY`, add a `RelinkSignal` instead.
3. Decrypt the access token with `EncryptionService`.
4. Call Plaid via `plaidEnvService.getClient().<method>(request).execute()`.
5. On a non-2xx response: classify the error with `PlaidClientFactory.classifyTokenError`. Token errors update the Item status and add a `RelinkSignal`. Other errors throw `RuntimeException` → 500.
6. Always return 200 with whatever data was collected plus a `relinkRequired` list.

### RelinkSignal
Signals a bank Item that needs user action. Included in every data endpoint response. See `ENDPOINTS.md` for the full field description.

---

## Authentication

Google OAuth2 via Spring Security. After login the server issues a session cookie (`JSESSIONID`). All `/api/**` routes (except `/api/dev/**`) require an active session; Spring redirects unauthenticated requests to `/oauth2/authorization/google`.

`SecurityUtils.resolveUser(principal, userRepository)` is the standard way for controllers to obtain the `User` entity from the authenticated principal.

---

## Database migrations

Flyway manages the schema under `src/main/resources/db/migration`. Migration files are named `V<n>__<description>.sql` and run automatically on startup.
