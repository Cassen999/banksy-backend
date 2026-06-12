# Diagrams — Recurring Transactions

## Request flow: GET /api/recurring (get-all)

```
Client
  │
  ▼
RecurringController.getRecurring(principal, accountId=null)
  │  resolveUser → userId
  ▼
RecurringService.getRecurring(userId, null)
  │  userRepository.findByIdWithPlaidItems(userId)
  │
  ├─ for each PlaidItem:
  │    ├─ status != HEALTHY → RelinkSignal, skip
  │    └─ HEALTHY → decrypt token → plaidClient.transactionsRecurringGet()
  │         ├─ token error → update status, RelinkSignal, continue
  │         ├─ other error → throw RuntimeException
  │         └─ success → map to TransactionStreamDto, add to inflow/outflow lists
  │
  ▼
RecurringResponse { inflowStreams, outflowStreams, relinkRequired }
  │
  ▼
200 OK
```

## Request flow: GET /api/recurring?accountId=xxx (per-account)

```
Client
  │
  ▼
RecurringController.getRecurring(principal, accountId="xxx")
  │  resolveUser → userId
  ▼
RecurringService.getRecurring(userId, "xxx")
  │  plaidAccountRepository.findByPlaidAccountIdWithItem("xxx")
  │    └─ absent → NoSuchElementException → 404
  │
  │  userRepository.findByIdWithPlaidItems(userId)
  │    └─ item not in user's list → SecurityException → 403
  │
  ├─ item.status != HEALTHY → RelinkSignal → return early (200)
  │
  └─ HEALTHY → decrypt token → plaidClient.transactionsRecurringGet(accountIds=["xxx"])
       ├─ token error → update status, RelinkSignal, return 200
       ├─ other error → throw RuntimeException → 500
       └─ success → map streams → RecurringResponse → 200
```

## Data model (response shape)

```
RecurringResponse
├── inflowStreams: List<TransactionStreamDto>
│     ├── accountId: String
│     ├── streamId: String
│     ├── merchantName: String
│     ├── description: String
│     ├── frequency: String        ("WEEKLY" | "BIWEEKLY" | "SEMI_MONTHLY" | "MONTHLY" | "ANNUALLY" | "UNKNOWN")
│     ├── firstDate: LocalDate
│     ├── lastDate: LocalDate
│     ├── predictedNextDate: LocalDate
│     ├── averageAmount: AmountDto { amount, isoCurrencyCode }
│     ├── lastAmount: AmountDto    { amount, isoCurrencyCode }
│     ├── isActive: Boolean
│     ├── personalFinanceCategory: PersonalFinanceCategoryDto { primary, detailed }
│     └── status: String           ("MATURE" | "EARLY_DETECTION" | "TOMBSTONED" | "UNKNOWN")
├── outflowStreams: List<TransactionStreamDto>  (same shape)
└── relinkRequired: List<RelinkSignal>
```
