# Implementation Plan: Revoked Token Recovery

## Goal

When a Plaid access token fails, detect which of two distinct failure cases occurred,
persist the status on the item, surface non-blocking relink prompts at login and in
data responses, and provide recovery endpoints for each case. Healthy banks are always
accessible regardless of the state of other banks.

---

## The Two Failure Cases

| Plaid Error Code | Meaning | Recovery |
|---|---|---|
| `ITEM_LOGIN_REQUIRED` | Credentials changed, MFA expired, session timeout | Plaid **update mode** — re-authenticates the existing item; access token may or may not change |
| `INVALID_ACCESS_TOKEN` | Item was deleted or explicitly revoked; token is dead | **Full re-link** — treat as a brand-new bank connection; migrate shared users; delete old item |

---

## ITEM_LOGIN_REQUIRED — Storage Question

**Nothing extra needs to be stored at re-auth time beyond the status reset.** The access
token may stay the same or change; the token comparison in `exchangeAndStore` handles
any update. `PlaidItem.updatedAt` refreshes automatically via `@PreUpdate`. No additional
columns are needed for this path beyond what is planned below.

---

## Design: Non-Blocking Response

Unhealthy banks never block access to healthy ones. The balance and transactions
endpoints always return `200` with:

- `accounts` / `transactions` — data from healthy items only
- `relinkRequired` — list of items needing relink (empty if none)

The frontend treats `relinkRequired` as a non-blocking notification. The user can act
on it immediately or dismiss it and continue using their healthy banks. A dedicated
login-check endpoint returns the same relink list without making any Plaid API calls,
allowing the frontend to show a prompt immediately after login.

---

## New: `PlaidItemStatus` enum and `status` column

### `PlaidItemStatus` enum

Location: `org.example.entity.PlaidItemStatus`

```java
public enum PlaidItemStatus { HEALTHY, NEEDS_REAUTH, INVALID_TOKEN }
```

### Migration: `V2__add_owner_and_status_to_plaid_items.sql`

Both the `owner_user_id` FK and the `status` column are introduced in the same migration
since they are part of the same feature.

```sql
ALTER TABLE plaid_items
    ADD COLUMN owner_user_id UUID NOT NULL REFERENCES users(id),
    ADD COLUMN status        VARCHAR(20) NOT NULL DEFAULT 'HEALTHY';

CREATE INDEX idx_plaid_items_owner_user_id ON plaid_items(owner_user_id);
```

**Pre-launch note:** `owner_user_id` is `NOT NULL` with no default. Safe because Banksy
has no production data yet. If ever run against live data, a two-step migration
(nullable → backfill → NOT NULL) would be required.

### `PlaidItem.java` — two new fields

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "owner_user_id", nullable = false)
private User owner;

@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, length = 20)
private PlaidItemStatus status = PlaidItemStatus.HEALTHY;

// + getOwner(), getStatus(), setStatus() accessors
```

`FetchType.LAZY` avoids loading the `User` on every `PlaidItem` access. Services that
need `owner` are annotated `@Transactional(readOnly = true)` so lazy loading works
safely within the session.

### Side effects on existing code

| Location | Change |
|---|---|
| `PlaidLinkService.exchangeAndStore` | Set `item.setOwner(user)` and `item.setStatus(HEALTHY)` when creating a new `PlaidItem` |
| `PlaidLinkService.exchangeAndStore` | Reset `existingItem.setStatus(HEALTHY)` after a successful token exchange |
| All tests that instantiate `PlaidItem` | Must set an owner and status — existing test stubs need updating |
| `PlaidItemRepositoryTest` | Assert owner and status are persisted and retrievable |

---

## New enum: `PlaidTokenError`

Location: `org.example.plaid.PlaidTokenError`

```java
public enum PlaidTokenError { LOGIN_REQUIRED, INVALID_TOKEN }
```

---

## New: `owner_user_id` on `PlaidItem`

### Why

`PlaidItem` has no concept of which user originally linked the bank. Without it,
`linkTokenRefresh` and `fullRelinkToken` cannot distinguish the owner from a shared user,
and the services cannot populate `ownerName` for the non-owner message.

---

## `PlaidClientFactory` — new classifier method

```java
public static Optional<PlaidTokenError> classifyTokenError(String errorBody)
```

- Accepts a pre-read error body string (caller reads it once via `extractErrorDetail`, then passes the string here — avoids consuming the Retrofit response stream twice)
- Returns `Optional.of(LOGIN_REQUIRED)` for `ITEM_LOGIN_REQUIRED`
- Returns `Optional.of(INVALID_TOKEN)` for `INVALID_ACCESS_TOKEN`
- Returns `Optional.empty()` for all other codes

Parsing: `String.contains` checks on the known error code strings — no new dependency.

---

## New model: `RelinkSignal`

Location: `org.example.model.RelinkSignal`

```java
public record RelinkSignal(
    UUID plaidItemId,
    String institutionName,
    PlaidTokenError errorType,
    boolean canRelink,
    String ownerName,   // null when canRelink is true
    String message
) {}
```

### Message values

| Situation | `message` |
|---|---|
| Owner, `LOGIN_REQUIRED` | `"Plaid authentication error, please try again."` |
| Owner, `INVALID_TOKEN` | `"This bank connection has been removed and must be re-linked."` |
| Non-owner, either error | `"Bank needs to be reauthenticated, contact <ownerName> and have them follow the prompts to re-authenticate this bank"` |

`ownerName` is `owner.getFirstName() + " " + owner.getLastName()`, falling back to
`owner.getUsername()` if both names are blank.

---

## `BalanceResponse` and `TransactionsResponse` — add `relinkRequired`

```java
public record BalanceResponse(List<Account> accounts, List<RelinkSignal> relinkRequired) { ... }
public record TransactionsResponse(List<Transaction> transactions, int count, List<RelinkSignal> relinkRequired) { ... }
```

Both fields are always present. When all items are healthy, `relinkRequired` is an
empty list. The frontend treats a non-empty `relinkRequired` as a non-blocking
notification — it does not prevent rendering `accounts` or `transactions`.

---

## `BalanceService` and `TransactionsService` — per-item status handling

Both services gain `@Transactional` on their main method so that lazy loading of
`owner` on `PlaidItem` works within the session, and so that status writes (when a
token error is first discovered) flush automatically via JPA dirty checking. `readOnly`
is intentionally omitted — these methods write `item.setStatus(...)` on first discovery.

Per-item logic for each `PlaidItem` in the user's list:

```
if item.status != HEALTHY:
    build RelinkSignal from stored data (no Plaid call)
    add to relinkRequired list
    skip to next item

else (status == HEALTHY):
    make Plaid API call
    if success:
        add data to accounts/transactions list
    if token error (classifyTokenError returns non-empty):
        write new status to item (NEEDS_REAUTH or INVALID_TOKEN)
        save item
        build RelinkSignal
        add to relinkRequired list
        skip to next item
    if other Plaid error:
        throw RuntimeException (hard failure, same as before)
```

Return response with healthy data + any relink signals. Always returns successfully
(no exception for token errors).

### Building a `RelinkSignal` from stored data

When an item is skipped (status already non-HEALTHY or just discovered):

```java
boolean isOwner = item.getOwner().getId().equals(userId);
PlaidTokenError errorType = (item.getStatus() == NEEDS_REAUTH)
    ? PlaidTokenError.LOGIN_REQUIRED
    : PlaidTokenError.INVALID_TOKEN;
```

---

## `BalanceController` and `TransactionsController` — always `200`

With non-blocking behavior, `403` is no longer used for token errors. Controllers
always return `200`:

```java
return ResponseEntity.ok(balanceService.getBalance(userId));
```

The `relinkRequired` field in the response body carries the notification to the
frontend. An empty list means all banks are healthy.

---

## New: `GET /api/plaid/status` — login-time check

Added to `PlaidLinkController`.

At login the frontend calls this endpoint. It queries the user's `PlaidItem`s, filters
to non-HEALTHY status, builds `RelinkSignal`s from stored data (no Plaid API call),
and returns:

```json
{ "relinkRequired": [ ... ] }
```

If empty, the frontend shows nothing. If non-empty, the frontend shows a non-blocking
prompt that the user can act on or dismiss.

New service method: `PlaidLinkService.getRelinkStatus(UUID userId)` — iterates the
user's items, builds signals for non-HEALTHY ones, returns the list.

---

## `PlaidLinkService` — five changes

### 1. `linkTokenRefresh` (LOGIN_REQUIRED path — update mode)

```java
public String linkTokenRefresh(UUID userId, UUID plaidItemId) throws IOException
```

- Loads `PlaidItem`, throws `IllegalArgumentException` if not found
- Checks `item.getOwner().getId().equals(userId)` — throws `SecurityException` if not owner
- Decrypts stored access token
- Calls `plaidClient.linkTokenCreate()` with `.accessToken(decryptedToken)` (update mode)
- Returns link token string
- On Plaid non-2xx: throws `RuntimeException` with full Plaid error body

### 2. `fullRelinkToken` (INVALID_TOKEN path — fresh link)

```java
public String fullRelinkToken(UUID userId, UUID plaidItemId) throws IOException
```

- Same owner validation as `linkTokenRefresh`
- Calls `plaidClient.linkTokenCreate()` with **no** `accessToken` — fresh link
- Returns link token string

### 3. `replaceExpiredItem` (called after exchange for INVALID_TOKEN path)

```java
@Transactional
public void replaceExpiredItem(UUID oldItemId, UUID newItemId)
```

- Loads old and new `PlaidItem`
- Calls `userRepository.findAllWithPlaidItem(oldItemId)` → every user sharing the old item
- For each user: removes old item, adds new item (within same transaction)
- Deletes old item — DB cascades handle `plaid_accounts` and `user_plaid_items` rows

Add new item to all users **before** deleting the old item — no user loses access
between the two operations.

### 4. `getRelinkStatus` (login-time check)

```java
public List<RelinkSignal> getRelinkStatus(UUID userId)
```

- Loads user's `PlaidItem`s
- Filters to non-HEALTHY status
- Builds `RelinkSignal` per item from stored data (no Plaid call)
- Returns list (empty if all healthy)

### 5. `exchangeAndStore` — token comparison, owner assignment, status reset

**On new item creation:**
```java
item.setOwner(user);
item.setStatus(PlaidItemStatus.HEALTHY);
```

**On existing item (token comparison + status reset):**
```java
String storedToken = encryptionService.decrypt(existingItem.getAccessTokenEnc());
if (!storedToken.equals(accessToken)) {
    existingItem.setAccessTokenEnc(encryptionService.encrypt(accessToken));
}
existingItem.setStatus(PlaidItemStatus.HEALTHY);  // always reset on successful exchange
plaidItemRepository.save(existingItem);
```

Status is always reset to `HEALTHY` on a successful exchange, regardless of whether
the token changed. This covers both the LOGIN_REQUIRED re-auth path and the
INVALID_TOKEN full-relink path (though the old item is deleted in that case, this
applies to the new item which starts HEALTHY by default).

---

## `UserRepository` — new query

```java
@Query("SELECT u FROM User u JOIN u.plaidItems pi WHERE pi.id = :plaidItemId")
List<User> findAllWithPlaidItem(@Param("plaidItemId") UUID plaidItemId);
```

---

## `PlaidLinkController` — updates

### New: `GET /api/plaid/status`

- Success: `200 { "relinkRequired": [...] }`
- No items needing relink: `200 { "relinkRequired": [] }`

### New: `GET /api/plaid/link-token/refresh/{itemId}` (LOGIN_REQUIRED)

- Success: `200 { "link_token": "..." }`
- Not owner: `403 { "error": "..." }`
- Plaid failure: `500 { "error": "<plaid error with suggested_action>" }`

### New: `GET /api/plaid/link-token/full-relink/{itemId}` (INVALID_TOKEN)

- Same response contract as the refresh endpoint above

### Updated: `POST /api/plaid/exchange`

`ExchangeRequest` gains an optional `expiredItemId` field:

```java
public record ExchangeRequest(String publicToken, String institutionId, String institutionName, UUID expiredItemId) {}
```

After successful `exchangeAndStore`:
- If `expiredItemId` non-null: call `plaidLinkService.replaceExpiredItem(expiredItemId, newItem.getId())`

Success response updated:
```java
Map.of("status", "ok", "message", "Plaid authentication successful")
```

The `message` field is now returned for both initial link and re-link calls.

---

## Future Update Required: Remove Bank Account

> **TODO — update this plan once `removeBank` is implemented.**

A user with a non-HEALTHY bank item may have simply closed that account at the bank.
In that case re-authentication will never succeed, and the relink prompt becomes a dead
end. The user needs a way out.

When `removeBank` exists, the revoked token flow must be updated to:

1. Surface a **"Remove this bank"** option alongside the relink prompt whenever
   `status` is `NEEDS_REAUTH` or `INVALID_TOKEN` — not just when the user navigates
   to a settings/manage-banks screen.
2. Confirm that `removeBank` correctly handles the non-HEALTHY status path — i.e., it
   does not attempt a Plaid `/item/remove` call when `status` is `INVALID_TOKEN`
   (the Plaid item is already gone; only the local DB records need cleanup).
3. If removing a shared bank from the owner also removes it for all shared users,
   verify that shared users with this item in `NEEDS_REAUTH` / `INVALID_TOKEN` state
   also lose access cleanly.

There are multiple expected removal entry points (settings page, relink prompt, admin
action) — ensure all of them reach the same `removeBank` service method so status
cleanup is consistent.

---

## Files Added

| File | Purpose |
|---|---|
| `src/main/java/org/example/entity/PlaidItemStatus.java` | Status enum |
| `src/main/java/org/example/plaid/PlaidTokenError.java` | Token error classification enum |
| `src/main/java/org/example/model/RelinkSignal.java` | Relink signal model record |
| `src/main/resources/db/migration/V2__add_owner_and_status_to_plaid_items.sql` | Adds `owner_user_id` FK and `status` column |

## Files Modified

| File | Change |
|---|---|
| `PlaidItem.java` | Add `owner` `@ManyToOne(LAZY)` and `status` fields + accessors |
| `PlaidClientFactory.java` | Add `classifyTokenError` method |
| `BalanceResponse.java` | Add `relinkRequired` field |
| `TransactionsResponse.java` | Add `relinkRequired` field |
| `BalanceService.java` | `@Transactional`; skip non-HEALTHY items; write status on first discovery |
| `TransactionsService.java` | Same as BalanceService |
| `PlaidLinkService.java` | `linkTokenRefresh`, `fullRelinkToken`, `replaceExpiredItem`, `getRelinkStatus`; `exchangeAndStore` token compare + owner + status |
| `PlaidLinkController.java` | `GET /api/plaid/status`; two new link token endpoints; `ExchangeRequest` + `expiredItemId`; exchange success message |
| `UserRepository.java` | Add `findAllWithPlaidItem` query |
