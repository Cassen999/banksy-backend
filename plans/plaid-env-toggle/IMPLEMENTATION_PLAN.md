# Implementation Plan — plaid-env-toggle

## Goal
Add a dev-only HTTP endpoint to toggle the Plaid API environment (sandbox ↔ production) at runtime without restarting the server. The active environment persists to the database across restarts.

## Architecture

The core problem: `PlaidApi` was a singleton Spring bean created once at startup. To support runtime switching, `PlaidApiConfig` is emptied and a new `PlaidEnvironmentService` holds a `volatile PlaidApi` reference that can be atomically replaced.

## Components

### New
- `V5__add_plaid_environment_config.sql` — single-row config table, seeded `sandbox`
- `PlaidEnvironmentConfig` (entity) — maps to the config table
- `PlaidEnvironmentConfigRepository` — JPA repo for the config row
- `PlaidEnvironmentService` — holds `volatile PlaidApi currentClient`; `init()` loads from DB; `toggle()` flips env, saves to DB, rebuilds client; `getClient()` returns current client
- `PlaidAdminController` — `GET /api/dev/plaid/environment` + `POST /api/dev/plaid/environment/toggle`

### Modified
- `PlaidConfig` — removed `getSecret()`, added `getSecretSandbox()` + `getSecretProduction()` reading new env vars `PLAID_SECRET_SANDBOX` and `PLAID_SECRET_PRODUCTION`
- `PlaidClientFactory` — added `create(String env)` overload that picks the correct secret per environment; `create()` delegates to it
- `PlaidApiConfig` — emptied (no longer registers `PlaidApi` bean)
- `SecurityConfig` — added `requestMatchers("/api/dev/**").permitAll()`
- `PlaidLinkService`, `TransactionsService`, `BalanceService`, `RemoveBankService` — injected `PlaidEnvironmentService` instead of `PlaidApi`; call `plaidEnvService.getClient()` per request
- `.env` / `.env.example` — renamed `PLAID_SECRET` to `PLAID_SECRET_SANDBOX`, added `PLAID_SECRET_PRODUCTION` placeholder

## Environment Toggle Details

`toggle()` is `synchronized` to prevent concurrent flips. The `currentClient` field is `volatile` so all threads immediately see the new reference after a toggle. DB persistence means the environment survives app restarts.
