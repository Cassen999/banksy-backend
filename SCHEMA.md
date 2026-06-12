# Database Schema Reference

> **Audience:** Frontend developers who need to understand the data model behind the Banksy API.
> This document is a read-only reference — the database is owned by the backend. You will never interact with these tables directly; this is context for understanding what the API returns and why.

---

## Entity-Relationship Diagram

```
┌──────────────────┐        ┌───────────────────────┐
│      users       │        │    oauth_identities   │
├──────────────────┤        ├───────────────────────┤
│ id (PK)          │◄───────│ user_id (FK)          │
│ first_name       │  1:N   │ id (PK)               │
│ last_name        │        │ provider              │
│ username (UQ)    │        │ provider_user_id      │
│ email (UQ)       │        │ created_at            │
│ created_at       │        └───────────────────────┘
│ updated_at       │
└────────┬─────────┘
         │
         │  M:N via user_plaid_items
         │
         ▼
┌──────────────────────┐       ┌───────────────────────┐
│   user_plaid_items   │       │    notifications      │
├──────────────────────┤       ├───────────────────────┤
│ user_id (FK, PK)     │       │ id (PK)               │
│ plaid_item_id (FK,PK)│       │ user_id (FK)          │
│ added_at             │       │ message               │
└──────────┬───────────┘       │ created_at            │
           │                   │ read                  │
           │                   └───────────────────────┘
           ▼
┌───────────────────────────┐
│        plaid_items        │
├───────────────────────────┤
│ id (PK)                   │◄──── owner_user_id (FK → users)
│ access_token_enc          │
│ item_id (UQ)              │
│ institution_id            │
│ institution_name          │
│ transaction_cursor        │
│ owner_user_id (FK)        │
│ status                    │
│ created_at                │
│ updated_at                │
└─────────────┬─────────────┘
              │ 1:N
              ▼
┌─────────────────────────────┐
│       plaid_accounts        │
├─────────────────────────────┤
│ id (PK)                     │
│ plaid_item_id (FK)          │
│ plaid_account_id (UQ)       │
│ name                        │
│ official_name               │
│ type                        │
│ subtype                     │
│ mask                        │
│ hidden                      │
│ created_at                  │
└─────────────────────────────┘

┌────────────────────────────┐
│  plaid_environment_config  │  ← singleton config row (id=1 always)
├────────────────────────────┤
│ id (PK, SERIAL)            │
│ env                        │
│ updated_at                 │
└────────────────────────────┘

┌────────────────────────────────┐
│   user_rejected_categories     │  ← per-user monthly-glance category filter
├────────────────────────────────┤
│ id (PK)                        │
│ user_id (FK → users)           │
│ category                       │
│ created_at                     │
└────────────────────────────────┘

┌────────────────────────────────┐
│    user_excluded_accounts      │  ← per-user monthly-glance account filter
├────────────────────────────────┤
│ id (PK)                        │
│ user_id (FK → users)           │
│ plaid_account_id               │
│ created_at                     │
└────────────────────────────────┘

┌────────────────────────────────┐
│       plaid_categories         │  ← read-only Plaid PFCv2 taxonomy (146 rows)
├────────────────────────────────┤
│ category (PK)                  │
│ category_type                  │
│ primary_category               │
└────────────────────────────────┘
```

---

## Tables

---

### `users`
The application's user accounts. Created automatically on first Google login.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Internal user identifier |
| `first_name` | `VARCHAR(100)` | NOT NULL | Given name (from Google profile) |
| `last_name` | `VARCHAR(100)` | NOT NULL | Family name (from Google profile) |
| `username` | `VARCHAR(50)` | NOT NULL, UNIQUE | Derived from email local part |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | Google account email |
| `created_at` | `TIMESTAMP` | NOT NULL, default `NOW()` | Account creation time |
| `updated_at` | `TIMESTAMP` | NOT NULL, default `NOW()` | Last profile update time |

**What the API surfaces from this table:**  
`GET /api/auth/me` returns `id`, `email`, `firstName`, `lastName`, `username`.

---

### `oauth_identities`
Links a user account to a specific OAuth provider identity. Supports multiple providers per user (currently only Google).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK | Identity record identifier |
| `user_id` | `UUID` | NOT NULL, FK → `users.id` (CASCADE) | The app user this identity belongs to |
| `provider` | `VARCHAR(50)` | NOT NULL | OAuth provider name (e.g. `"google"`) |
| `provider_user_id` | `VARCHAR(255)` | NOT NULL | Provider's own identifier for this account |
| `created_at` | `TIMESTAMP` | NOT NULL | When this identity was first linked |

**Unique constraint:** `(provider, provider_user_id)` — prevents duplicate identities.  
**What the API surfaces:** Nothing directly; used internally to match returning OAuth logins to existing users.

---

### `plaid_items`
Represents a connected bank institution. One item = one bank login, which may have multiple accounts underneath it. Items are created when a user completes the Plaid Link flow.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK | Internal item identifier — this is the `plaidItemId` used in API calls |
| `access_token_enc` | `TEXT` | NOT NULL | AES-256-GCM encrypted Plaid access token |
| `item_id` | `VARCHAR(255)` | NOT NULL, UNIQUE | Plaid's own item identifier |
| `institution_id` | `VARCHAR(255)` | NOT NULL | Plaid institution ID (e.g. `"ins_3"`) |
| `institution_name` | `VARCHAR(255)` | NOT NULL | Human-readable bank name (e.g. `"Chase"`) |
| `transaction_cursor` | `TEXT` | nullable | Plaid transaction sync cursor for incremental fetches |
| `owner_user_id` | `UUID` | NOT NULL, FK → `users.id` | The user who originally linked this bank |
| `status` | `VARCHAR(20)` | NOT NULL, default `'HEALTHY'` | Health state — see values below |
| `created_at` | `TIMESTAMP` | NOT NULL | When the item was first linked |
| `updated_at` | `TIMESTAMP` | NOT NULL | Last modification time |

**`status` values:**

| Value | Meaning | Action required |
|-------|---------|-----------------|
| `HEALTHY` | Token is valid; data calls succeed | None |
| `NEEDS_REAUTH` | Credentials expired (Plaid `ITEM_LOGIN_REQUIRED`) | Re-authenticate via `GET /api/plaid/link-token/refresh/{itemId}` |
| `INVALID_TOKEN` | Item revoked/deleted at Plaid (`INVALID_ACCESS_TOKEN`) | Full re-link via `GET /api/plaid/link-token/full-relink/{itemId}` |

**Sharing model:** An item can be shared with other users via `user_plaid_items`. The `owner_user_id` column identifies who originally linked it — only the owner can re-authenticate it.

---

### `plaid_accounts`
Individual bank accounts within a `plaid_items` connection (e.g. a checking account and a savings account at the same bank are two rows here under the same item).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK | Internal account identifier |
| `plaid_item_id` | `UUID` | NOT NULL, FK → `plaid_items.id` (CASCADE) | The parent bank item |
| `plaid_account_id` | `VARCHAR(255)` | NOT NULL, UNIQUE | Plaid's own account identifier |
| `name` | `VARCHAR(255)` | NOT NULL | Account display name (e.g. `"Plaid Checking"`) |
| `official_name` | `VARCHAR(255)` | nullable | Bank's official product name (e.g. `"Plaid Gold Standard 0% Interest Checking"`) |
| `type` | `VARCHAR(50)` | NOT NULL | Plaid account type: `depository`, `credit`, `loan`, `investment`, `other` |
| `subtype` | `VARCHAR(50)` | nullable | Plaid account subtype: `checking`, `savings`, `credit card`, etc. |
| `mask` | `VARCHAR(4)` | nullable | Last 4 digits of the account number |
| `hidden` | `BOOLEAN` | NOT NULL, default `FALSE` | When `true`, this account is excluded from balance and transaction API responses |
| `created_at` | `TIMESTAMP` | NOT NULL | When the account record was created |

**What the API surfaces from this table:**  
Balance and transaction responses include only accounts where `hidden = false`. The `id` field appears as the identifier used when hiding an account via `PUT /api/plaid/account/{plaidAccountId}/hide`. The `plaid_account_id` field is surfaced as `accountId` in `GET /api/balance` responses so the frontend can reference specific accounts (e.g. for per-account recurring transaction requests).

---

### `user_plaid_items`
Join table for the many-to-many relationship between users and plaid items. A row here means a user can see and use that bank connection.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `user_id` | `UUID` | PK, FK → `users.id` (CASCADE) | The user |
| `plaid_item_id` | `UUID` | PK, FK → `plaid_items.id` (CASCADE) | The bank item |
| `added_at` | `TIMESTAMP` | NOT NULL | When the link was created |

**Composite PK:** `(user_id, plaid_item_id)`.

**Rows are created in two ways:**
1. When a user links a bank themselves — `POST /api/plaid/exchange`.
2. When an owner shares a bank with another user — `POST /api/plaid/share`.

---

### `notifications`
In-app notifications for a user. Currently generated by the server after hide/remove operations to inform all linked users of the outcome.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK | Notification identifier |
| `user_id` | `UUID` | NOT NULL, FK → `users.id` (CASCADE) | Recipient user |
| `message` | `TEXT` | NOT NULL | Human-readable notification body |
| `created_at` | `TIMESTAMP` | NOT NULL | When the notification was created |
| `read` | `BOOLEAN` | NOT NULL, default `FALSE` | Whether the user has read this notification |

**Note:** There is currently no API endpoint to fetch or mark notifications as read. This table is written to by the backend but not yet surfaced to the frontend.

---

### `plaid_environment_config`
Singleton configuration table for the active Plaid environment. Always contains exactly one row (`id = 1`).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `SERIAL` | PK | Always `1` |
| `env` | `VARCHAR(20)` | NOT NULL, default `'sandbox'` | Active environment: `'sandbox'` or `'production'` |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | When the environment was last toggled |

**What the API surfaces:** `GET /api/dev/plaid/environment` and `POST /api/dev/plaid/environment/toggle`. Not visible to end users.

---

### `user_rejected_categories`
Per-user list of Plaid PFCv2 category strings to exclude from `GET /api/monthly-glance`. Extends the hardcoded default set (`RENT_AND_UTILITIES`, `INCOME`, `TRANSFER_IN`, `LOAN_DISBURSEMENTS`) defined in `MonthlyGlanceService`.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Record identifier |
| `user_id` | `UUID` | NOT NULL, FK → `users.id` (CASCADE) | The user who added this rejection |
| `category` | `VARCHAR(255)` | NOT NULL | A Plaid PFCv2 primary or detailed category string (e.g. `"PERSONAL_CARE"` or `"FOOD_AND_DRINK_COFFEE"`) |
| `created_at` | `TIMESTAMP` | NOT NULL, default `now()` | When the rejection was added |

**Unique constraint:** `(user_id, category)` — prevents duplicates per user.  
**Written by:** a future settings UI. **Read by:** `MonthlyGlanceService` at request time.

---

### `user_excluded_accounts`
Per-user list of Plaid account ID strings to exclude from `GET /api/monthly-glance`. Useful for users who want to omit a business account or joint account from their personal spending graph.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Record identifier |
| `user_id` | `UUID` | NOT NULL, FK → `users.id` (CASCADE) | The user who excluded this account |
| `plaid_account_id` | `VARCHAR(255)` | NOT NULL | Plaid-assigned account ID string (matches `plaid_accounts.plaid_account_id`) |
| `created_at` | `TIMESTAMP` | NOT NULL, default `now()` | When the exclusion was added |

**Unique constraint:** `(user_id, plaid_account_id)` — prevents duplicates per user.  
**Written by:** a future settings UI. **Read by:** `MonthlyGlanceService` at request time.

---

### `plaid_categories`
Read-only lookup table of all 146 Plaid PFCv2 taxonomy entries. Seeded once by `V8__seed_plaid_categories.sql`. Never written at runtime.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `category` | `VARCHAR(255)` | PK | The taxonomy string itself (e.g. `"FOOD_AND_DRINK"` or `"FOOD_AND_DRINK_COFFEE"`) |
| `category_type` | `VARCHAR(8)` | NOT NULL | `"PRIMARY"` (18 rows) or `"DETAILED"` (128 rows) |
| `primary_category` | `VARCHAR(255)` | nullable | Null for primary-level rows; the parent primary string for detailed rows |

**18 primary groups:** INCOME, LOAN_DISBURSEMENTS, LOAN_PAYMENTS, TRANSFER_IN, TRANSFER_OUT, BANK_FEES, ENTERTAINMENT, FOOD_AND_DRINK, GENERAL_MERCHANDISE, HOME_IMPROVEMENT, MEDICAL, PERSONAL_CARE, GENERAL_SERVICES, GOVERNMENT_AND_NON_PROFIT, TRANSPORTATION, TRAVEL, RENT_AND_UTILITIES, OTHER.  
**Surfaced by:** `GET /api/categories`.

---

## Key Relationships Summary

```
users (1) ──────────────── (N) oauth_identities
  │                                   (login matching)
  │
  ├── (owner) ────────── (N) plaid_items
  │                            │
  │                            └── (1) ──── (N) plaid_accounts
  │
  ├── (M:N via user_plaid_items) ──── plaid_items
  │                                        (shared access)
  │
  ├── (1) ──────────────── (N) notifications
  ├── (1) ──────────────── (N) user_rejected_categories
  └── (1) ──────────────── (N) user_excluded_accounts

plaid_environment_config  ← singleton (id=1)
plaid_categories          ← read-only taxonomy (146 rows, no FK)
```

- A user can **own** many items and be **shared on** many items.
- Owning vs. sharing determines who can re-authenticate when a token expires.
- Hiding an account (`hidden = true`) is per-account, not per-item; any linked user can hide.
- Removing an item (`DELETE /api/plaid/item/{id}`) cascades: deletes the item, all its accounts, and all `user_plaid_items` rows, then notifies every previously linked user.
- `user_rejected_categories` and `user_excluded_accounts` are per-user settings for `GET /api/monthly-glance` filtering; they cascade on user delete.
