# Implementation Plan: Custom Account Names

## Goal
Allow users to assign custom display names to their linked bank accounts. The custom name is stored per-user per-account and returned in the balance response so the frontend can override the default label.

## New files
- `V9__add_user_account_names.sql` — migration
- `UserAccountName.java` — entity
- `UserAccountNameRepository.java` — repository
- `AccountCustomizationService.java` — service
- `AccountCustomizationController.java` — controller
- `AccountCustomizationServiceTest.java` — service tests
- `AccountCustomizationControllerTest.java` — controller tests

## Modified files
- `BalanceResponse.java` — add `customName` field to Account
- `BalanceService.java` — inject repo, load names, pass to toAccount
- `BalanceServiceTest.java` — inject mock, update existing tests, add customName test
- `BalanceControllerTest.java` — update Account constructor call
- `ARCHITECTURE.md`, `ENDPOINTS.md`, `SCHEMA.md` — documentation
