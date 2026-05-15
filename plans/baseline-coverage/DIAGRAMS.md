# Diagrams: Baseline Test Coverage

## System Layer Architecture

```mermaid
graph TD
    subgraph Controllers["Controllers (Integration Tests — MockMvc)"]
        AC[AuthController]
        PC[PlaidLinkController]
        BC[BalanceController]
        TC[TransactionsController]
    end

    subgraph Services["Services (Unit Tests — Mockito)"]
        ES[EncryptionService]
        CS[CustomOAuth2UserService]
        PLS[PlaidLinkService]
        BS[BalanceService]
        TS[TransactionsService]
    end

    subgraph Utils["Utilities (Unit Tests — Mockito)"]
        SU[SecurityUtils]
    end

    subgraph Repositories["Repositories (Integration Tests — Testcontainers)"]
        UR[UserRepository]
        OR[OAuthIdentityRepository]
        PIR[PlaidItemRepository]
        PAR[PlaidAccountRepository]
    end

    subgraph External["External (Mocked in all tests)"]
        PA[PlaidApi]
        DB[(PostgreSQL)]
    end

    subgraph Excluded["Excluded from JaCoCo"]
        MAIN[Main.java]
        CFG[config/*]
        ENT[entity/*]
        GAT[GenerateAccessToken]
        PCF[PlaidClientFactory]
    end

    AC --> SU
    PC --> SU
    BC --> SU
    TC --> SU

    AC --> UR
    PC --> PLS
    BC --> BS
    TC --> TS

    PLS --> PA
    PLS --> ES
    PLS --> PIR
    PLS --> PAR
    PLS --> UR

    BS --> PA
    BS --> ES
    BS --> UR

    TS --> PA
    TS --> ES
    TS --> UR

    CS --> UR
    CS --> OR

    Repositories --> DB
```

---

## Test Coverage Matrix

```
┌──────────────────────────────┬─────────────┬────────────────────┬──────────────────────────┐
│ Class                        │ Test Type   │ Tools              │ Dependencies Mocked       │
├──────────────────────────────┼─────────────┼────────────────────┼──────────────────────────┤
│ EncryptionService            │ Unit        │ JUnit 5            │ none (uses real crypto)  │
│ SecurityUtils                │ Unit        │ Mockito            │ UserRepository           │
│ CustomOAuth2UserService      │ Unit        │ Mockito spy        │ super.loadUser(), repos  │
│ PlaidLinkService             │ Unit        │ Mockito            │ PlaidApi, repos, Encrypt │
│ BalanceService               │ Unit        │ Mockito            │ PlaidApi, repos, Encrypt │
│ TransactionsService          │ Unit        │ Mockito            │ PlaidApi, repos, Encrypt │
├──────────────────────────────┼─────────────┼────────────────────┼──────────────────────────┤
│ AuthController               │ Integration │ MockMvc, oidcLogin │ UserRepository           │
│ PlaidLinkController          │ Integration │ MockMvc, oidcLogin │ PlaidLinkService, UserRepo│
│ BalanceController            │ Integration │ MockMvc, oidcLogin │ BalanceService, UserRepo │
│ TransactionsController       │ Integration │ MockMvc, oidcLogin │ TransactionsSvc, UserRepo│
├──────────────────────────────┼─────────────┼────────────────────┼──────────────────────────┤
│ UserRepository               │ Integration │ Testcontainers     │ none (real PostgreSQL)   │
│ OAuthIdentityRepository      │ Integration │ Testcontainers     │ none (real PostgreSQL)   │
│ PlaidItemRepository          │ Integration │ Testcontainers     │ none (real PostgreSQL)   │
│ PlaidAccountRepository       │ Integration │ Testcontainers     │ none (real PostgreSQL)   │
└──────────────────────────────┴─────────────┴────────────────────┴──────────────────────────┘
```

---

## Testcontainers Database Flow

```
@DataJpaTest + @AutoConfigureTestDatabase(replace = NONE)
         │
         ▼
AbstractRepositoryTest
  @Container static PostgreSQLContainer
         │
         ▼
  @DynamicPropertySource overrides
  spring.datasource.url / username / password
         │
         ▼
  Flyway runs V1__init_schema.sql
         │
         ▼
  Test inserts data → asserts repository queries
```
