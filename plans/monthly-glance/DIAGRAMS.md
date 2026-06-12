# Diagrams: monthly-glance

## Request Flow

```
GET /api/monthly-glance
        │
        ▼
MonthlyGlanceController
  - resolveUser(principal) → userId
        │
        ▼
MonthlyGlanceService.getMonthlyGlance(userId)
  │
  ├─ UserRepository.findByIdWithPlaidItems(userId)
  ├─ UserRejectedCategoryRepository.findCategoriesByUserId(userId)  ─┐
  ├─ UserExcludedAccountRepository.findPlaidAccountIdsByUserId(userId)┘ combined into filter sets
  │
  └─ for each PlaidItem
        │
        ├─ [non-HEALTHY] → add RelinkSignal, skip
        │
        └─ [HEALTHY]
              │
              ├─ decrypt accessToken
              ├─ Plaid: transactionsGet(startDate=1st, endDate=today)
              ├─ [token error] → update status, add RelinkSignal, skip
              ├─ [other error] → throw RuntimeException
              │
              ├─ PlaidAccountRepository.findHiddenAccountIdsByItemId(item)
              │
              └─ stream transactions
                    ├─ filter: exclude hidden accounts
                    ├─ filter: exclude user-excluded accounts
                    ├─ filter: exclude rejected primary/detailed category (null pfc → include)
                    └─ accumulate: dailyTotals.merge(date, amount, Double::sum)

  └─ fill result
        ├─ iterate day 1 → today
        └─ each day: getOrDefault(date, 0.0)

        ▼
MonthlyGlanceResponse {
  dailyTotals: [{ transactionDate: "2026-06-01", total: 45.20 }, ...],
  relinkRequired: [...]
}
```

## Data Model

```
users ──────────────────────────────────┐
  │                                     │
  ├── user_rejected_categories          │  (per-user bill category exclusions)
  │     user_id FK                      │  category values come from plaid_categories
  │     category VARCHAR                │
  │                                     │
  └── user_excluded_accounts            │  (per-user account exclusions)
        user_id FK                      │
        plaid_account_id VARCHAR        │
                                        │
plaid_accounts ─────────────────────────┘
  hidden BOOLEAN  (global hide — also excluded)

plaid_categories  (read-only reference, seeded by V8)
  category VARCHAR PK         e.g. "RENT_AND_UTILITIES_RENT"
  category_type VARCHAR(8)    "PRIMARY" or "DETAILED"
  primary_category VARCHAR    null for PRIMARY rows; parent name for DETAILED rows
```

## Category Search Flow (future UI)

```
GET /api/categories
        │
        ▼
CategoriesController
        │
        ▼
PlaidCategoryRepository.findAll()
        │
        ▼
[{ category: "RENT_AND_UTILITIES_RENT", type: "DETAILED", primaryCategory: "RENT_AND_UTILITIES" }, ...]
        │
        ▼  (frontend: user searches "gym", selects PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS)
        │
        ▼
POST /api/users/me/rejected-categories  (future endpoint)
  body: { category: "PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS" }
        │
        ▼
user_rejected_categories row inserted → excluded from next monthly-glance call
```

## Filtering Pipeline (per transaction)

```
transaction
    │
    ├─ accountId in hiddenIds?          → EXCLUDE
    ├─ accountId in excludedAccountIds? → EXCLUDE
    ├─ pfc == null?                     → INCLUDE
    ├─ pfc.primary in rejectedCats?     → EXCLUDE
    ├─ pfc.detailed in rejectedCats?    → EXCLUDE
    └─ (otherwise)                      → INCLUDE → accumulate into dailyTotals
```
