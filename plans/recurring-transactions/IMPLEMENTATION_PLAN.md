# Implementation Plan — Recurring Transactions

## Files to modify

### `src/main/java/org/example/repository/PlaidAccountRepository.java`
Add query method:
```java
@Query("SELECT a FROM PlaidAccount a JOIN FETCH a.plaidItem WHERE a.plaidAccountId = :plaidAccountId")
Optional<PlaidAccount> findByPlaidAccountIdWithItem(@Param("plaidAccountId") String plaidAccountId);
```

## Files to create

### `src/main/java/org/example/model/RecurringResponse.java`
Record with nested records:
- `TransactionStreamDto` — 13 fields (accountId, streamId, merchantName, description, frequency, firstDate, lastDate, predictedNextDate, averageAmount, lastAmount, isActive, personalFinanceCategory, status)
- `AmountDto` — (amount, isoCurrencyCode)
- `PersonalFinanceCategoryDto` — (primary, detailed)

### `src/main/java/org/example/service/RecurringService.java`
- `getRecurring(UUID userId, String accountId)` — dispatches to get-all or per-account
- Get-all: loop `user.getPlaidItems()`, call Plaid for each HEALTHY item, aggregate
- Per-account: `findByPlaidAccountIdWithItem` → 404 if absent; check user linked → 403; call Plaid with accountIds filter
- Token error handling identical to BalanceService
- `toDto(TransactionStream)` — maps Plaid SDK object to `TransactionStreamDto`

### `src/main/java/org/example/controller/RecurringController.java`
- `GET /api/recurring` with optional `?accountId=` query param
- Delegates to `RecurringService.getRecurring`
- `NoSuchElementException` → 404, `SecurityException` → 403, `Exception` → 500

## Files to update

### `ENDPOINTS.md`
Add `GET /api/recurring` section with both modes, query params, and full response shape.

### `ARCHITECTURE.md` (create if missing)
Document the layered architecture and include `RecurringService` / `RecurringController`.
