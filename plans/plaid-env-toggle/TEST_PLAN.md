# Test Plan — plaid-env-toggle

## Unit Tests

### PlaidEnvironmentServiceTest
- `shouldReturnCurrentEnvironment` — getCurrentEnvironment() reads env from DB
- `shouldReturnNonNullClient` — getClient() returns the initialized client after init()
- `shouldToggleFromSandboxToProduction` — starting from sandbox, toggle() returns "production" and saves to DB
- `shouldToggleFromProductionBackToSandbox` — starting from production, toggle() returns "sandbox"
- `shouldReturnDifferentClientInstance_afterToggle` — client reference changes after toggle

### PlaidAdminControllerTest
- `shouldReturnCurrentEnvironment` — GET /api/dev/plaid/environment returns {"environment":"sandbox"}
- `shouldReturnNewEnvironmentAfterToggle` — POST /api/dev/plaid/environment/toggle returns new env
- `shouldRedirectToOAuth_whenUnauthenticated` — unauthenticated request returns 3xx

### PlaidEnvironmentConfigRepositoryTest
- `shouldLoadSeedRow` — migration seed row exists with env="sandbox"
- `shouldPersistEnvUpdate` — can update env and read it back

## Coverage Requirements
- Line coverage ≥ 90% (project-wide)
- Branch coverage ≥ 90% (project-wide)
