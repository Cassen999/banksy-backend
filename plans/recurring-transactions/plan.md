# Recurring Transactions Plan

## Goal
`GET /api/recurring` — fetches recurring transaction streams from Plaid.

## Files to create/change
- `PlaidAccountRepository` — add `findByPlaidAccountIdWithItem`
- `model/RecurringResponse` — new response DTO
- `service/RecurringService` — business logic
- `controller/RecurringController` — HTTP layer
- `RecurringServiceTest` / `RecurringControllerTest` — tests
- `ENDPOINTS.md` — document new endpoint
- `ARCHITECTURE.md` — update with new service/controller

## Modes
- Get-all (no `accountId` param): loop all HEALTHY items, aggregate streams
- Per-account (`accountId` param): resolve item from account, verify user linked, call Plaid with filter

## Error handling
- Non-HEALTHY item → skip + add RelinkSignal (same as BalanceService)
- Token error at call time → update item status + add RelinkSignal
- Non-token Plaid error → throw RuntimeException → 500
- accountId not found → NoSuchElementException → 404
- User not linked → SecurityException → 403
