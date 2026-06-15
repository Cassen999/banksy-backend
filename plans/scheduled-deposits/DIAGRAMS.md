# Diagrams — Scheduled Deposits

## Request flow: GET /api/recurring/scheduled-deposits

```
Client
  │
  ▼
RecurringController.getScheduledDeposits(principal)
  │  resolveUser → userId
  ▼
RecurringService.getScheduledDeposits(userId)
  │
  ▼
RecurringService.getAllRecurring(userId)   ← existing method, now package-private
  │  userRepository.findByIdWithPlaidItems(userId)
  │
  ├─ for each PlaidItem:
  │    ├─ status != HEALTHY → RelinkSignal, skip
  │    └─ HEALTHY → decrypt token → plaidClient.transactionsRecurringGet()
  │         ├─ token error → update status, RelinkSignal, continue
  │         ├─ other error → throw RuntimeException → 403 "Error getting scheduled deposit data"
  │         └─ success → map to TransactionStreamDto, add to inflow/outflow lists
  │
  ▼
RecurringResponse { inflowStreams, outflowStreams, relinkRequired }
  │  take inflowStreams only
  │  filter: isActive=true, predictedNextDate in current calendar month
  │  sort: predictedNextDate ascending
  │  map: TransactionStreamDto → ScheduledDepositDto (drop accountId, streamId)
  ▼
List<ScheduledDepositDto>
  │
  ▼
200 OK
```

## Data model (response shape)

```
List<ScheduledDepositDto>
  └── ScheduledDepositDto
        ├── merchantName: String?
        ├── description: String?
        ├── frequency: String         ("WEEKLY" | "BIWEEKLY" | "SEMI_MONTHLY" | "MONTHLY" | "ANNUALLY" | "UNKNOWN")
        ├── firstDate: LocalDate
        ├── lastDate: LocalDate?
        ├── predictedNextDate: LocalDate   ← always within current calendar month
        ├── averageAmount: AmountDto?  { amount, isoCurrencyCode }
        ├── lastAmount: AmountDto?     { amount, isoCurrencyCode }
        ├── isActive: Boolean          ← always true (filtered)
        ├── personalFinanceCategory: PersonalFinanceCategoryDto?  { primary, detailed }
        └── status: String             ("MATURE" | "EARLY_DETECTION" | "TOMBSTONED" | "UNKNOWN")
```

## Relationship to existing recurring endpoint

```
GET /api/recurring                      GET /api/recurring/scheduled-deposits
      │                                           │
      ▼                                           ▼
getAllRecurring(userId)  ←──── reused ────  getScheduledDeposits(userId)
      │                                           │
      ▼                                           ▼
RecurringResponse                       List<ScheduledDepositDto>
  inflowStreams  (all time)               (current month only, inflow only)
  outflowStreams (all time)
  relinkRequired
```
