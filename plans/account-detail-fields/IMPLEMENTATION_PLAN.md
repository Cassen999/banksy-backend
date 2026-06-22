# Implementation Plan: Account Detail Fields

## Goal
Add `accountId` to each transaction in `/api/transactions` and add `institutionName` to each account in `/api/balance` so the frontend can build a per-account detail page.

## Files to Modify

1. `src/main/java/org/example/model/TransactionsResponse.java` — add `accountId` field to `Transaction` record
2. `src/main/java/org/example/service/TransactionsService.java` — pass `t.getAccountId()` in mapper
3. `src/main/java/org/example/model/BalanceResponse.java` — add `institutionName` field to `Account` record
4. `src/main/java/org/example/service/BalanceService.java` — update `toAccount` to accept and pass `institutionName`
5. `src/test/java/org/example/service/TransactionsServiceTest.java` — stub and assert `accountId`
6. `src/test/java/org/example/service/BalanceServiceTest.java` — stub and assert `institutionName`
7. `documentation/ENDPOINTS.md` — update response JSON examples
