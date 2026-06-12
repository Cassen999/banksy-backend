# Recurring Transactions Endpoint

Summary: Create a /api/recurring endpoint that fetches recurring transaction streams from Plaid for a user's linked
bank accounts. The endpoint supports two modes: get-all (aggregates across every linked Item) and per-account (scoped
to a single account by ID).

  ---
## Background

Plaid's recurring transactions endpoint is POST /transactions/recurring/get. It requires an access_token (which is per Item, not per account — one Item can contain multiple accounts) and accepts an optional account_ids array to filter results within that Item. It returns two arrays: inflowStreams and outflowStreams, each containing TransactionStream objects.

The frontend will obtain accountId values from the existing GET /api/balance response (recently updated to include accountId). The backend resolves which Item owns a given accountId via the database.

  ---
## Requirements

1. New endpoint: GET /api/recurring

Support two modes via an optional accountId query parameter:

- Get-all (GET /api/recurring): Loop over all of the authenticated user's linked Items (same pattern as BalanceService and TransactionsService). For each HEALTHY Item, call Plaid's /transactions/recurring/get with that Item's access_token. Aggregate all results into a single flat response.
- Per-account (GET /api/recurring?accountId={accountId}): Look up which Item owns the given accountId in the database, verify the calling user is linked to that Item, then call Plaid with that Item's access_token and pass the accountId in the account_ids filter.

2. Authentication & user resolution

Resolve the authenticated user from the session using the existing SecurityUtils.resolveUser() pattern. The user's linked Items come from the database — do not add a new endpoint for this; follow the same
userRepository.findByIdWithPlaidItems(userId) pattern used in BalanceService.

3. Error handling

Follow the exact same non-HEALTHY Item pattern as BalanceService:
- Skip non-HEALTHY Items and add a RelinkSignal to the response instead of calling Plaid
- On a token error discovered at call time, update the Item's status and add a RelinkSignal
- Always return 200 with whatever data was successfully retrieved plus a relinkRequired list

4. Response shape

{
"inflowStreams": [ ...TransactionStream... ],
"outflowStreams": [ ...TransactionStream... ],
"relinkRequired": []
}

For the get-all case, inflowStreams and outflowStreams are flat arrays merged across all healthy Items. Each
TransactionStream already contains an accountId field so the frontend can correlate streams back to specific accounts.

5. Plaid TransactionStream fields to include in the response

Map the following fields from Plaid's TransactionStream object:

┌───────────────────┬─────────────────────────────┐
│          Field          │            Type             │
├─────────────────────────┼─────────────────────────────┤
│ accountId               │ String                      │
├─────────────────────────┼─────────────────────────────┤
│ streamId                │ String                      │
├─────────────────────────┼─────────────────────────────┤
│ merchantName            │ String                      │
├─────────────────────────┼─────────────────────────────┤
│ description             │ String                      │
├─────────────────────────┼─────────────────────────────┤
│ frequency               │ String (enum value)         │
├─────────────────────────┼─────────────────────────────┤
│ firstDate               │ LocalDate                   │
├─────────────────────────┼─────────────────────────────┤
│ lastDate                │ LocalDate                   │
├─────────────────────────┼─────────────────────────────┤
│ predictedNextDate       │ LocalDate                   │
├─────────────────────────┼─────────────────────────────┤
│ averageAmount           │ { amount, isoCurrencyCode } │
├─────────────────────────┼─────────────────────────────┤
│ lastAmount              │ { amount, isoCurrencyCode } │
├─────────────────────────┼─────────────────────────────┤
│ isActive                │ Boolean                     │
├─────────────────────────┼─────────────────────────────┤
│ personalFinanceCategory │ { primary, detailed }       │
├─────────────────────────┼─────────────────────────────┤
│ status                  │ String (enum value)         │
└─────────────────────────┴─────────────────────────────┘
  ---
status enum values:

┌─────────────────┬──────────────────────────────────────────────────────────────────────────────────────┐
│      Value      │                                       Meaning                                        │
├─────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ MATURE          │ At least 3 transactions on a regular cadence — reliable stream                       │
├─────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ EARLY_DETECTION │ 1–2 transactions seen — Plaid suspects recurring but not yet confirmed               │
├─────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ TOMBSTONED      │ Was early detection but expected transaction never arrived — likely a false positive │
├─────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ UNKNOWN         │ Does not fit any of the above                                                        │
└─────────────────┴──────────────────────────────────────────────────────────────────────────────────────┘

The frontend may use status to badge new streams (EARLY_DETECTION), hide false positives (TOMBSTONED), or surface only
confident data (MATURE).

All streams are returned regardless of isActive value. The frontend is responsible for filtering.

6. Repository: per-account Item lookup

The per-account mode receives a Plaid account ID string from the frontend (e.g.
"BxBXxLj1m4HMXBm9WZZmCWVbPjX16EHwv99vp"). The existing PlaidAccountRepository has no method to resolve a PlaidAccount
and its parent PlaidItem from this string. Add the following method to PlaidAccountRepository:

@Query("SELECT a FROM PlaidAccount a JOIN FETCH a.plaidItem WHERE a.plaidAccountId = :plaidAccountId")
Optional<PlaidAccount> findByPlaidAccountIdWithItem(@Param("plaidAccountId") String plaidAccountId);

Use this in RecurringService to resolve the parent Item for a given accountId, then verify the calling user is linked
to that Item before proceeding.

If the accountId is not found in the database, return 404. If the accountId is found but the calling user is not
linked to its parent Item, return 403.

7. Tests
Write a RecurringControllerTest and a RecurringServiceTest following the same patterns
as BalanceControllerTest / BalanceServiceTest.

8. Documentation

- Update ENDPOINTS.md with the new endpoint, both modes, request params, and full response shape
- No SCHEMA.md update needed — this feature adds no new database tables or columns
- Update ARCHITECTURE.md with the new service and controller
