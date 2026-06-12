# Diagrams — plaid-env-toggle

## Runtime Toggle Flow

```
POST /api/dev/plaid/environment/toggle
        |
PlaidAdminController.toggleEnvironment()
        |
PlaidEnvironmentService.toggle()  [synchronized]
        |
        +-- repo.findById(1) ──> PlaidEnvironmentConfig (current env)
        |
        +-- compute next env (sandbox <-> production)
        |
        +-- config.setEnv(next)
        +-- repo.save(config) ──> DB persisted
        |
        +-- PlaidClientFactory.create(next)
        |        |
        |        +-- PlaidConfig.getSecretSandbox() or getSecretProduction()
        |        +-- build new Retrofit PlaidApi client
        |
        +-- currentClient = newClient  [volatile write]
        |
        return next env name
```

## Service Layer — PlaidApi Access

```
Before (singleton bean):
  PlaidLinkService ──┐
  BalanceService ────+──> @Bean PlaidApi (singleton, fixed at startup)
  TransactionsService┘
  RemoveBankService ─┘

After (mutable provider):
  PlaidLinkService ──┐
  BalanceService ────+──> PlaidEnvironmentService.getClient()  [volatile read]
  TransactionsService┘         |
  RemoveBankService ─┘         +──> PlaidApi (current environment)
```

## Database

```
plaid_environment_config
┌────┬──────────────┬────────────────────────┐
│ id │ env          │ updated_at             │
├────┼──────────────┼────────────────────────┤
│  1 │ sandbox      │ 2026-06-09 ...         │  ← always exactly one row
└────┴──────────────┴────────────────────────┘
```
