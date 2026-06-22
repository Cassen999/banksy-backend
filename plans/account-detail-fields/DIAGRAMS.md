# Diagrams: Account Detail Fields

## Data flow for account detail page

```
Frontend
  │
  ├── GET /api/balance
  │     └── BalanceResponse.Account { accountId, name, type, subtype, institutionName, ... }
  │
  └── GET /api/transactions?days=30
        └── TransactionsResponse.Transaction { accountId, date, name, amount, ... }

Frontend joins on accountId to show per-account transaction list.
```
