# Endpoints Reference

> **Audience:** Frontend developers consuming the Banksy API.
> **Auth model:** Session cookie (`JSESSIONID`). After OAuth login the browser holds the cookie automatically. All endpoints under `/api/**` require it except `/api/dev/**`.
> **Base URL (dev):** `http://localhost:8080`
> **CORS:** Credentialed requests (`withCredentials: true`) are allowed from `localhost:3000` and `localhost:5173`.

---

## Common patterns

### Authentication
All `/api/**` endpoints (except `/api/dev/**`) require an active session. If the session is missing or expired the server returns `302 → /oauth2/authorization/google`.

### Error shape
Endpoints that return a JSON error body use:
```json
{ "error": "<human-readable message>" }
```
Endpoints that don't return a body on error return an empty `500`.

### RelinkSignal shape
Several endpoints embed `relinkRequired: RelinkSignal[]`. This signals that one or more bank connections need attention. The field is always present; an empty array means all banks are healthy.

```json
{
  "plaidItemId": "uuid",
  "institutionName": "Chase",
  "errorType": "LOGIN_REQUIRED | INVALID_TOKEN",
  "canRelink": true,
  "ownerName": null,
  "message": "Plaid authentication error, please try again."
}
```

- `canRelink: true` — the current user is the owner of this item and can re-authenticate it.
- `canRelink: false` — the item is shared; `ownerName` identifies who must fix it.
- `errorType: "LOGIN_REQUIRED"` → use update-mode relink (`/link-token/refresh/{itemId}`).
- `errorType: "INVALID_TOKEN"` → use full relink (`/link-token/full-relink/{itemId}`).

---

## Auth / Session

### `GET /oauth2/authorization/google`
Initiates the Google OAuth2 login flow. Browser redirects to Google; on success Google redirects back and the server creates a session.

**Auth required:** No  
**Call:**
```js
window.location.href = 'http://localhost:8080/oauth2/authorization/google';
```

---

### `GET /logout`
Invalidates the server session and redirects the browser to the frontend URL.

**Auth required:** No  
**Call:**
```js
window.location.href = 'http://localhost:8080/logout';
```

> Note: This is a browser navigation, not an `axios`/`fetch` call, because the server sends a redirect response that the browser must follow.

---

### `GET /api/auth/me`
Returns the current user's profile.

**Auth required:** Yes  
**Call:**
```js
GET /api/auth/me
// No body, no query params
```

**Success `200`:**
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "username": "janedoe"
}
```

**Failures:**

| Status | Condition |
|--------|-----------|
| `302` | No active session |
| `500` | Unexpected server error (empty body) |

---

## Balance

### `GET /api/balance`
Returns live account balances for all of the current user's linked banks. Non-HEALTHY items are skipped and returned as `relinkRequired` signals instead.

**Auth required:** Yes  
**Call:**
```js
GET /api/balance
// No body, no query params
```

**Success `200`:**
```json
{
  "accounts": [
    {
      "accountId": "BxBXxLj1m4HMXBm9WZZmCWVbPjX16EHwv99vp",
      "name": "Plaid Checking",
      "type": "depository",
      "subtype": "checking",
      "currentBalance": 1500.00,
      "availableBalance": 1400.00,
      "currency": "USD"
    }
  ],
  "relinkRequired": []
}
```

- `accountId` — Plaid's account identifier; use this when requesting per-account data (e.g. recurring transactions).
- `accounts` — only includes accounts from HEALTHY items with `hidden = false`.
- `relinkRequired` — one entry per non-HEALTHY item the user has linked.

**Failures:**

| Status | Condition |
|--------|-----------|
| `302` | No active session |
| `500` | Unexpected server error (empty body) |

---

## Transactions

### `GET /api/transactions`
Returns transactions for all of the current user's linked banks. Non-HEALTHY items are skipped and returned as `relinkRequired` signals instead.

**Auth required:** Yes  
**Call:**
```js
GET /api/transactions
GET /api/transactions?days=90   // optional; defaults to 30
```

**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `days` | `int` | `30` | How many calendar days back to fetch |

**Success `200`:**
```json
{
  "transactions": [
    {
      "date": "2026-05-15",
      "name": "Starbucks",
      "amount": 5.75,
      "currency": "USD",
      "category": ["Food and Drink", "Restaurants", "Coffee Shop"]
    }
  ],
  "total": 1,
  "relinkRequired": []
}
```

- `amount` — positive = money leaving the account (debit); negative = money entering (credit). This is Plaid's native convention.
- `date` — ISO-8601 date string (`YYYY-MM-DD`).
- `category` — Plaid's hierarchical category list; may be empty.
- `transactions` — only includes transactions from HEALTHY items with `hidden = false` accounts.

**Failures:**

| Status | Condition |
|--------|-----------|
| `302` | No active session |
| `500` | Unexpected server error (empty body) |

---

## Plaid Link

### `GET /api/plaid/link-token`
Creates a new Plaid Link token to start the initial bank-connection flow.

**Auth required:** Yes  
**Call:**
```js
GET /api/plaid/link-token
// No body, no query params
```

**Success `200`:**
```json
{ "link_token": "link-sandbox-..." }
```

Pass this token to Plaid Link (`usePlaidLink({ token })`) to open the consent UI.

**Failures:**

| Status | Condition |
|--------|-----------|
| `302` | No active session |
| `500` | Plaid API error or unexpected server error (empty body) |

---

### `GET /api/plaid/link-token/refresh/{itemId}`
Creates an update-mode link token for a bank item whose credentials have expired (`errorType: "LOGIN_REQUIRED"`). The user re-authenticates without going through bank selection.

**Auth required:** Yes  
**Path params:**

| Param | Type | Description |
|-------|------|-------------|
| `itemId` | UUID | `plaidItemId` from a `RelinkSignal` |

**Call:**
```js
GET /api/plaid/link-token/refresh/{itemId}
```

**Success `200`:**
```json
{ "link_token": "link-sandbox-..." }
```

**Failures:**

| Status | Body | Condition |
|--------|------|-----------|
| `302` | — | No active session |
| `403` | `{ "error": "..." }` | Caller is not the owner of this item |
| `500` | `{ "error": "..." }` | Plaid API error or unexpected server error |

---

### `GET /api/plaid/link-token/full-relink/{itemId}`
Creates a fresh link token for a bank item that must be fully re-linked (`errorType: "INVALID_TOKEN"`). Opens the full bank-selection and credential flow.

**Auth required:** Yes  
**Path params:**

| Param | Type | Description |
|-------|------|-------------|
| `itemId` | UUID | `plaidItemId` from a `RelinkSignal` |

**Call:**
```js
GET /api/plaid/link-token/full-relink/{itemId}
```

**Success `200`:**
```json
{ "link_token": "link-sandbox-..." }
```

**Failures:**

| Status | Body | Condition |
|--------|------|-----------|
| `302` | — | No active session |
| `403` | `{ "error": "..." }` | Caller is not the owner of this item |
| `500` | `{ "error": "..." }` | Plaid API error or unexpected server error |

---

### `GET /api/plaid/status`
Returns the relink status for all of the current user's linked items. Call this at login time to decide whether to show a relink banner.

**Auth required:** Yes  
**Call:**
```js
GET /api/plaid/status
```

**Success `200`:**
```json
{
  "relinkRequired": []
}
```

- Empty array = all items are HEALTHY.
- Each entry in the array is a `RelinkSignal` (see top of this file).

**Failures:**

| Status | Condition |
|--------|-----------|
| `302` | No active session |
| `500` | Unexpected server error (empty body) |

---

### `POST /api/plaid/exchange`
Exchanges a Plaid `public_token` (returned by the Plaid Link `onSuccess` callback) for a stored access token. Call this immediately after the user completes the Plaid Link flow.

**Auth required:** Yes  
**Body:**
```json
{
  "publicToken": "public-sandbox-...",
  "institutionId": "ins_3",
  "institutionName": "Chase",
  "expiredItemId": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `publicToken` | `string` | Yes | Token from Plaid Link `onSuccess` |
| `institutionId` | `string` | Yes | Institution ID from Plaid Link metadata |
| `institutionName` | `string` | Yes | Institution name from Plaid Link metadata |
| `expiredItemId` | UUID or `null` | No | When re-linking an INVALID_TOKEN item, pass the old `plaidItemId` so the server can migrate shared users to the new item |

**Success `200`:**
```json
{ "status": "ok", "message": "Plaid authentication successful" }
```

**Failures:**

| Status | Condition |
|--------|-----------|
| `302` | No active session |
| `500` | Plaid token exchange failed or unexpected server error (empty body) |

---

### `POST /api/plaid/share`
Shares one of the current user's bank connections with another user by email. Only the item's owner can share it.

**Auth required:** Yes  
**Body:**
```json
{
  "plaidItemId": "uuid",
  "shareWithEmail": "friend@example.com"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `plaidItemId` | UUID | Yes | ID of the item to share (must be owned by the caller) |
| `shareWithEmail` | `string` | Yes | Email of the recipient user (must already have an account) |

**Success `200`:**
```json
{ "status": "ok" }
```

**Failures:**

| Status | Body | Condition |
|--------|------|-----------|
| `302` | — | No active session |
| `400` | `{ "error": "..." }` | Target user not found, item not found, or item already shared with that user |
| `403` | `{ "error": "..." }` | Caller is not the owner of this item |
| `500` | — | Unexpected server error (empty body) |

---

## Remove Bank

### `PUT /api/plaid/account/{plaidAccountId}/hide`
Soft-hides a single account. Hidden accounts are excluded from balance and transaction responses without removing them from Plaid. Any linked user (owner or shared) can hide an account.

**Auth required:** Yes  
**Path params:**

| Param | Type | Description |
|-------|------|-------------|
| `plaidAccountId` | UUID | Internal ID of the account to hide (from balance/transactions data) |

**Call:**
```js
PUT /api/plaid/account/{plaidAccountId}/hide
// No body
```

**Success `200`:**
```json
{ "status": "ok" }
```

**Failures:**

| Status | Body | Condition |
|--------|------|-----------|
| `302` | — | No active session |
| `403` | `{ "error": "..." }` | Caller is not linked to the item this account belongs to |
| `500` | `{ "error": "..." }` | Account not found or unexpected server error |

---

### `DELETE /api/plaid/item/{plaidItemId}`
Fully removes a bank item: revokes the access token at Plaid and deletes the item and all its accounts from the database. Any linked user (owner or shared) can trigger this. All previously linked users receive an in-app notification.

**Auth required:** Yes  
**Path params:**

| Param | Type | Description |
|-------|------|-------------|
| `plaidItemId` | UUID | Internal ID of the item to remove |

**Call:**
```js
DELETE /api/plaid/item/{plaidItemId}
// No body
```

**Success `200`:**
```json
{ "status": "ok" }
```

**Failures:**

| Status | Body | Condition |
|--------|------|-----------|
| `302` | — | No active session |
| `403` | `{ "error": "..." }` | Caller is not linked to this item |
| `500` | `{ "error": "..." }` | Plaid revocation error or unexpected server error |

---

## Dev (internal tooling — never expose to users)

> These endpoints are unauthenticated and hidden by URL only. They must never be linked from any UI.

### `GET /api/dev/plaid/environment`
Returns the currently active Plaid environment.

**Auth required:** No  
**Call:**
```
GET /api/dev/plaid/environment
```

**Success `200`:**
```json
{ "environment": "sandbox" }
```

Possible values: `"sandbox"`, `"production"`.

---

### `POST /api/dev/plaid/environment/toggle`
Toggles the Plaid environment between sandbox and production at runtime. Persists across server restarts.

**Auth required:** No  
**Call:**
```
POST /api/dev/plaid/environment/toggle
// No body
```

**Success `200`:**
```json
{ "environment": "production" }
```

Returns the **new** environment after the toggle.
