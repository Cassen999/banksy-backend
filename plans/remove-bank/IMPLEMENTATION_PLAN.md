# Implementation Plan — remove-bank

## Overview

Two distinct removal operations exposed through a single endpoint. The frontend specifies which operation to perform via a request field. Both operations are available to any user linked to the item (owner or shared).

---

## Operations

### Operation 1 — Soft Hide (PlaidAccount level)

Used when a user wants to hide one account from a PlaidItem that has **more than one account**. Does not touch Plaid. No Plaid API call is made.

- Adds a `hidden = true` flag to the target `PlaidAccount` row
- Hidden accounts are excluded from all API responses (balance, transactions)
- The PlaidItem and all other accounts under it remain fully active
- Notification sent to all users linked to the PlaidItem: `"Removed bank account ending in <mask>"`

**When this applies:** Frontend sends `operation: "HIDE_ACCOUNT"` with a `plaidAccountId`.

---

### Operation 2 — Full Item Removal (PlaidItem level)

Used when a user wants to fully sever a bank connection. Removes the PlaidItem and all its accounts for every linked user.

- If `PlaidItem.status == INVALID_TOKEN`: skip Plaid API call (token already dead), go straight to DB cleanup
- Otherwise: call Plaid `/item/remove` to revoke the access token
- Delete all `user_plaid_items` join rows for this item
- Hard delete the `PlaidItem` (cascades to all `PlaidAccount` rows)
- Notification sent to all users previously linked: `"Removed <institutionName> from Banksy, in order to see these accounts again you must re-link bank."`

**When this applies:** Frontend sends `operation: "REMOVE_ITEM"` with a `plaidItemId`.

---

### Error handling (both operations)

If either operation fails at any point, send a notification to all users linked to the item:
`"There was an error removing your account, please try again in a few minutes or contact Cassen"`

No partial state should be persisted. Both operations are `@Transactional`.

---

## Data Model Changes

### PlaidAccount — add `hidden` column

```sql
ALTER TABLE plaid_accounts ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
```

Flyway migration: `V4__add_hidden_to_plaid_accounts.sql`

No other schema changes required. `mask` is already stored on `PlaidAccount`.

---

## New Components

### `RemoveBankService`

New service class. Handles both operations. Injected with:
- `PlaidItemRepository`
- `PlaidAccountRepository`
- `UserRepository`
- `NotificationRepository`
- `PlaidApi`
- `EncryptionService`

**Methods:**

`hideAccount(UUID plaidAccountId, UUID requestingUserId)`
- Verify the requesting user is linked to the PlaidItem that owns this account (via `user_plaid_items`). Throw `SecurityException` if not.
- Fetch all users linked to the item.
- Set `hidden = true` on the `PlaidAccount`. Save.
- On success: create a `Notification` for each linked user with the mask message.
- On failure: create a `Notification` for each linked user with the error message. Re-throw.

`removeItem(UUID plaidItemId, UUID requestingUserId)`
- Verify the requesting user is linked to this `PlaidItem`. Throw `SecurityException` if not.
- Fetch all linked users before deletion (needed for notifications after rows are gone).
- If `PlaidItem.status != INVALID_TOKEN`: decrypt access token, call Plaid `/item/remove`.
- Remove all `user_plaid_items` join rows.
- Hard delete `PlaidItem` (cascades to `PlaidAccount`).
- On success: create a `Notification` for each previously linked user with the institution message.
- On failure: create a `Notification` for each linked user with the error message. Re-throw.

---

### `RemoveBankController`

New controller. Two endpoints — one per operation.

**Soft hide (state change only):**
`PUT /api/plaid/account/{plaidAccountId}/hide`
- No request body required; account ID is in the path
- Responses: `200 OK`, `403 Forbidden`, `500 Internal Server Error`

**Full item removal (true deletion):**
`DELETE /api/plaid/item/{plaidItemId}`
- No request body required; item ID is in the path
- Responses: `200 OK`, `403 Forbidden`, `500 Internal Server Error`

---

## Affected Existing Components

### `BalanceService` and `TransactionsService`

Both services iterate over accounts for a user. They must be updated to skip accounts where `hidden = true`.

### `PlaidAccountRepository`

May need a query method to look up a `PlaidAccount` with its parent `PlaidItem` in one query (to avoid N+1 when verifying user access from an account ID).

---

## Implementation Order

1. `V4__add_hidden_to_plaid_accounts.sql` — migration
2. `PlaidAccount.java` — add `hidden` field, getter, setter
3. `PlaidAccountRepository.java` — add fetch-join query if needed
4. `RemoveBankService.java` + `RemoveBankServiceTest.java`
5. `RemoveBankController.java` + `RemoveBankControllerTest.java`
6. `BalanceService.java` — filter hidden accounts
7. `TransactionsService.java` — filter hidden accounts
8. `ARCHITECTURE.md` — document all new and modified components