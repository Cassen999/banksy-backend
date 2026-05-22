# Test Plan — remove-bank

---

## RemoveBankServiceTest

### hideAccount — happy path

| # | Scenario | Setup | Expected |
|---|---|---|---|
| 1 | Owner hides an account | PlaidItem with 2 accounts, requesting user is owner | account.hidden = true, success notification created for all linked users |
| 2 | Shared user hides an account | PlaidItem shared between 2 users, requester is non-owner | account.hidden = true, success notification created for both users |
| 3 | Notification message contains correct mask | PlaidAccount.mask = "4242" | notification message = "Removed bank account ending in 4242" |

### hideAccount — error paths

| # | Scenario | Setup | Expected |
|---|---|---|---|
| 4 | Requesting user not linked to item | User has no row in user_plaid_items for this item | SecurityException thrown, no account modified |
| 5 | Account not found | Non-existent plaidAccountId | Exception thrown, no notification created |
| 6 | DB save fails | PlaidAccountRepository.save throws | Error notification created for all linked users, exception re-thrown |

---

## RemoveBankServiceTest — removeItem

### removeItem — happy path

| # | Scenario | Setup | Expected |
|---|---|---|---|
| 7 | Owner removes item (HEALTHY status) | PlaidItem with HEALTHY status, owner requests removal | Plaid /item/remove called, PlaidItem deleted, join rows deleted, success notifications created |
| 8 | Shared user removes item | Non-owner in user_plaid_items requests removal | Same as above — all users lose access, all notified |
| 9 | Remove item with INVALID_TOKEN status | PlaidItem.status = INVALID_TOKEN | Plaid /item/remove NOT called, DB cleanup proceeds, success notifications created |
| 10 | Remove item with NEEDS_REAUTH status | PlaidItem.status = NEEDS_REAUTH | Plaid /item/remove called normally (token still valid), DB cleanup, notifications |
| 11 | Notification message contains institution name | institutionName = "Chase" | notification = "Removed Chase from Banksy, in order to see these accounts again you must re-link bank." |
| 12 | Users snapshot captured before deletion | Item shared by 2 users | Both users receive notification even though join rows are deleted |

### removeItem — error paths

| # | Scenario | Setup | Expected |
|---|---|---|---|
| 13 | Requesting user not linked to item | No join row for requesting user | SecurityException thrown, no deletion |
| 14 | Item not found | Non-existent plaidItemId | Exception thrown |
| 15 | Plaid API call fails | PlaidApi.itemRemove throws | Error notification sent to all linked users, exception re-thrown, no DB deletion |
| 16 | DB deletion fails after Plaid call | PlaidItemRepository.delete throws | Error notification sent, exception re-thrown |

---

## RemoveBankControllerTest

### PUT /api/plaid/account/{plaidAccountId}/hide

| # | Scenario | Expected HTTP |
|---|---|---|
| 17 | Valid plaidAccountId, authenticated user | 200 OK |
| 18 | User not linked to the account's item | 403 Forbidden |
| 19 | Service throws unexpected exception | 500 Internal Server Error |
| 20 | Unauthenticated request | 401 Unauthorized (Spring Security) |

### DELETE /api/plaid/item/{plaidItemId}

| # | Scenario | Expected HTTP |
|---|---|---|
| 21 | Valid plaidItemId, authenticated user | 200 OK |
| 22 | User not linked to the item | 403 Forbidden |
| 23 | Service throws unexpected exception | 500 Internal Server Error |
| 24 | Unauthenticated request | 401 Unauthorized (Spring Security) |

---

## BalanceService / TransactionsService — hidden account filter

| # | Scenario | Expected |
|---|---|---|
| 25 | User has one hidden account, one visible | Only visible account returned in balance response |
| 26 | All accounts under an item are hidden | Empty account list returned (no Plaid call made for hidden accounts) |
| 27 | No hidden accounts | Behavior unchanged from current |

---

## Coverage Requirements

- `RemoveBankService`: 90% line and branch
- `RemoveBankController`: 90% line and branch
- Existing services (`BalanceService`, `TransactionsService`): existing tests must be updated to cover the hidden-filter branch; coverage must not regress