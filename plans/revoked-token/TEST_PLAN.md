# Test Plan: Revoked Token Recovery

---

## PlaidItemStatus (no test needed)

Pure enum — no logic to test.

## PlaidTokenError (no test needed)

Pure enum — no logic to test.

---

## PlaidClientFactory (Unit)

| Test | Condition |
|---|---|
| `shouldReturnLoginRequired_whenErrorCodeIsItemLoginRequired` | error body contains `"ITEM_LOGIN_REQUIRED"` → `Optional.of(LOGIN_REQUIRED)` |
| `shouldReturnInvalidToken_whenErrorCodeIsInvalidAccessToken` | error body contains `"INVALID_ACCESS_TOKEN"` → `Optional.of(INVALID_TOKEN)` |
| `shouldReturnEmpty_whenErrorCodeIsUnrelated` | error body contains a different code → `Optional.empty()` |
| `shouldReturnEmpty_whenErrorBodyIsNull` | `response.errorBody()` is null → `Optional.empty()` |

---

## BalanceService (Unit)

| Test | Condition |
|---|---|
| `shouldSkipItemAndReturnLoginRequiredSignal_whenItemStatusIsNeedsReauth` | item status already `NEEDS_REAUTH`; no Plaid call made; `RelinkSignal` built from stored data with `errorType: LOGIN_REQUIRED` |
| `shouldSkipItemAndReturnInvalidTokenSignal_whenItemStatusIsInvalidToken` | item status already `INVALID_TOKEN`; no Plaid call made; `RelinkSignal` with `errorType: INVALID_TOKEN` |
| `shouldWriteStatusAndReturnSignal_whenHealthyItemReceivesLoginRequiredError` | item is `HEALTHY`; Plaid returns `ITEM_LOGIN_REQUIRED`; item status written to `NEEDS_REAUTH`; `RelinkSignal` added; save called |
| `shouldWriteStatusAndReturnSignal_whenHealthyItemReceivesInvalidTokenError` | item is `HEALTHY`; Plaid returns `INVALID_ACCESS_TOKEN`; item status written to `INVALID_TOKEN`; `RelinkSignal` added |
| `shouldReturnOwnerSignal_whenCurrentUserIsOwner` | `canRelink: true`; `ownerName: null`; message is owner-appropriate |
| `shouldReturnNonOwnerSignal_whenCurrentUserIsNotOwner` | `canRelink: false`; `ownerName` is owner's full name; message is "contact <ownerName>..." |
| `shouldFallBackToUsername_whenOwnerHasBlankFirstAndLastName` | `ownerName` falls back to `owner.getUsername()` |
| `shouldReturnPartialAccounts_whenOneItemFailsAndOtherSucceeds` | one item unhealthy, one healthy; `accounts` has data from healthy item only |
| `shouldReturnEmptyAccounts_whenAllItemsAreUnhealthy` | all items non-HEALTHY; `accounts` empty; `relinkRequired` has entry per item |
| `shouldThrowRuntimeException_whenHealthyItemPlaidCallFailsWithNonTokenError` | Plaid returns non-2xx with unrelated error code; `classifyTokenError` returns empty; `RuntimeException` thrown |
| `shouldReturnEmptyRelinkRequired_whenAllItemsAreHealthy` | all items healthy, all Plaid calls succeed; `relinkRequired` is empty |

---

## TransactionsService (Unit)

| Test | Condition |
|---|---|
| `shouldSkipItemAndReturnSignal_whenItemStatusIsNonHealthy` | mirrors BalanceService skip pattern |
| `shouldWriteStatusAndReturnSignal_whenHealthyItemReceivesTokenError` | mirrors BalanceService first-discovery pattern |
| `shouldReturnPartialTransactions_whenOneItemUnhealthy` | partial data, one item skipped |
| `shouldThrowRuntimeException_whenNonTokenErrorOccurs` | hard fail unchanged |
| `shouldReturnEmptyRelinkRequired_whenAllItemsHealthy` | happy path unchanged |

---

## PlaidLinkService (Unit)

### New: `linkTokenRefresh`

| Test | Condition |
|---|---|
| `shouldReturnLinkToken_whenOwnerRequestsRefresh` | user is owner; Plaid returns 200 |
| `shouldThrowSecurityException_whenNonOwnerRequestsRefresh` | `item.getOwner().getId()` does not match requesting user |
| `shouldThrowIllegalArgument_whenItemNotFoundOnRefresh` | `plaidItemRepository.findById` returns empty |
| `shouldThrowRuntimeException_whenPlaidRefreshCallFails` | Plaid returns non-2xx |

### New: `fullRelinkToken`

| Test | Condition |
|---|---|
| `shouldReturnLinkToken_whenOwnerRequestsFullRelink` | user is owner; Plaid returns 200; no access token in request |
| `shouldThrowSecurityException_whenNonOwnerRequestsFullRelink` | owner check fails |
| `shouldThrowIllegalArgument_whenItemNotFoundOnFullRelink` | item not in DB |
| `shouldThrowRuntimeException_whenPlaidFullRelinkCallFails` | Plaid non-2xx |

### New: `replaceExpiredItem`

| Test | Condition |
|---|---|
| `shouldMigrateAllUsersToNewItem_whenOldItemIsShared` | two users share old item; after replace, both have new item, neither has old item |
| `shouldMigrateSingleOwner_whenItemIsNotShared` | one user; migrated to new item |
| `shouldDeleteOldItem_afterMigration` | `plaidItemRepository.delete` called with old item |
| `shouldThrowException_whenOldItemNotFound` | `findById(oldItemId)` returns empty |
| `shouldThrowException_whenNewItemNotFound` | `findById(newItemId)` returns empty |

### New: `getRelinkStatus`

| Test | Condition |
|---|---|
| `shouldReturnSignalsForNonHealthyItems_whenSomeItemsNeedRelink` | user has mix of healthy and non-healthy items; only non-healthy returned |
| `shouldReturnEmptyList_whenAllItemsAreHealthy` | no non-HEALTHY items |
| `shouldReturnEmptyList_whenUserHasNoPlaidItems` | user has no linked banks |
| `shouldBuildSignalFromStoredData_withoutCallingPlaid` | no Plaid API call made during status check |

### Updated: `exchangeAndStore`

| Test | Condition |
|---|---|
| `shouldUpdateStoredAccessToken_whenExistingItemHasDifferentToken` | tokens differ; `save` called with new encrypted token |
| `shouldResetStatusToHealthy_whenExchangeSucceeds` | existing item had `NEEDS_REAUTH` status; after exchange, status is `HEALTHY` |
| `shouldNotUpdateToken_whenExistingItemHasSameToken` | tokens match; token field not re-encrypted; status still reset to `HEALTHY` and saved |
| `shouldSetOwnerAndHealthyStatus_whenCreatingNewItem` | new item; `owner` is requesting user; `status` is `HEALTHY` |

---

## BalanceController (Integration — MockMvc)

| Test | Condition |
|---|---|
| `shouldReturn200WithPartialDataAndRelinkSignal_whenSomeItemsNeedRelink` | `relinkRequired` non-empty; controller returns `200` — response body has both `accounts` and `relinkRequired` |
| `shouldReturn200WithFullData_whenAllItemsHealthy` | `relinkRequired` empty; `200` with `accounts` and empty `relinkRequired` |

---

## TransactionsController (Integration — MockMvc)

| Test | Condition |
|---|---|
| `shouldReturn200WithPartialDataAndRelinkSignal_whenSomeItemsNeedRelink` | mirrors BalanceController |
| `shouldReturn200WithFullData_whenAllItemsHealthy` | mirrors BalanceController |

---

## PlaidLinkController (Integration — MockMvc)

### `GET /api/plaid/status`

| Test | Condition |
|---|---|
| `shouldReturnRelinkList_whenUserHasNonHealthyItems` | `200 { "relinkRequired": [...] }` with entries |
| `shouldReturnEmptyList_whenAllItemsHealthy` | `200 { "relinkRequired": [] }` |

### `GET /api/plaid/link-token/refresh/{itemId}`

| Test | Condition |
|---|---|
| `shouldReturnLinkToken_whenRefreshSucceeds` | `200 { "link_token": "..." }` |
| `shouldReturn403_whenRefreshThrowsSecurityException` | service throws `SecurityException` |
| `shouldReturn500WithError_whenRefreshThrowsRuntimeException` | Plaid error propagated in body |

### `GET /api/plaid/link-token/full-relink/{itemId}`

| Test | Condition |
|---|---|
| `shouldReturnLinkToken_whenFullRelinkSucceeds` | `200 { "link_token": "..." }` |
| `shouldReturn403_whenFullRelinkThrowsSecurityException` | non-owner |
| `shouldReturn500WithError_whenFullRelinkThrowsRuntimeException` | Plaid error propagated |

### `POST /api/plaid/exchange`

| Test | Condition |
|---|---|
| `shouldReturnSuccessMessage_whenExchangeSucceeds` | response body includes `"message": "Plaid authentication successful"` |
| `shouldCallReplaceExpiredItem_whenExpiredItemIdIsPresent` | `expiredItemId` non-null; `replaceExpiredItem` called after `exchangeAndStore` |
| `shouldNotCallReplaceExpiredItem_whenExpiredItemIdIsAbsent` | `expiredItemId` is null; `replaceExpiredItem` never called |

---

## UserRepository (Integration — Testcontainers)

| Test | Condition |
|---|---|
| `shouldReturnAllUsersWithItem_whenItemIsShared` | two users share same `PlaidItem`; `findAllWithPlaidItem` returns both |
| `shouldReturnSingleUser_whenItemIsNotShared` | one user has item; returns list of one |
| `shouldReturnEmpty_whenNoUserHasItem` | no users in join table for item |

---

## PlaidItemRepository (Integration — Testcontainers)

| Test | Condition |
|---|---|
| `shouldPersistOwnerAndStatus_whenPlaidItemIsSaved` | saved `PlaidItem` has correct `owner_user_id` and `status`; `findById` returns item with both fields |
| `shouldDefaultToHealthy_whenStatusNotExplicitlySet` | item created without explicit status; DB default `HEALTHY` is returned |

---

## Edge Cases and Failure Scenarios Summary

- **Non-blocking healthy access**: unhealthy items are always skipped; user sees healthy banks regardless of other banks' state
- **First discovery**: item with `HEALTHY` status gets a token error; status is written to DB immediately; item skipped for this response; subsequent requests use stored status (no Plaid call)
- **Login-time check**: `GET /api/plaid/status` reads stored status only — no Plaid calls; non-blocking prompt shown if any items need relink
- **Non-owner relink signal**: `canRelink: false`; owner name in message; frontend shows info message only
- **Owner name fallback**: `firstName` and `lastName` both blank → use `username`
- **INVALID_TOKEN + shared users**: `replaceExpiredItem` migrates all users atomically; no user loses bank access
- **Token unchanged after re-auth**: `exchangeAndStore` detects equality, skips token write, still resets status to `HEALTHY`
- **All items unhealthy**: `accounts`/`transactions` are empty lists; `relinkRequired` has an entry per item; still `200`
- **`expiredItemId` absent from exchange body**: normal initial-link flow unchanged
- **Concurrent full-relink calls**: second `replaceExpiredItem` call for a now-deleted `oldItemId` throws on `findById` → 500; acceptable since first call already succeeded
